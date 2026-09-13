// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import pro.javacard.capfile.CAPFile;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OffCardVerifier {
    private final JavaCardSDK sdk;

    public static OffCardVerifier withSDK(JavaCardSDK sdk) {
        // Only main method in 2.1 SDK
        if (SDKRelease.NO_VERIFIER.contains(sdk.getRelease())) {
            throw new RuntimeException("Verification is supported with JavaCard SDK 2.2.1 or later");
        }
        return new OffCardVerifier(sdk);
    }

    private OffCardVerifier(JavaCardSDK sdk) {
        this.sdk = sdk;
    }

    public void verifyAgainst(File f, JavaCardAPI target, Vector<File> exps) throws VerifierError, IOException {
        List<Path> exports = new ArrayList<>(exps.stream().map(File::toPath).collect(Collectors.toList()));
        target.exportDir().ifPresent(exports::add);
        verify(f.toPath(), exports);
    }

    // Verify a given CAP file against a set of EXP files
    public void verify(Path f, List<Path> exps) throws VerifierError, IOException {
        verify(f, exps, null);
    }

    // SDK messages go to the given handler
    public void verify(Path f, List<Path> exps, Handler handler) throws VerifierError, IOException {
        Path tmp = Files.createTempDirectory("capfile");
        SDKLogger.SDKLog capture = SDKLogger.capture(handler);
        try (InputStream in = Files.newInputStream(f);
             URLClassLoader loader = sdk.getClassLoader()) {
            CAPFile cap = CAPFile.fromStream(in);

            // Get verifier class
            Class<?> verifier = Class.forName("com.sun.javacard.offcardverifier.Verifier", true, loader);

            final Vector<File> expfiles = new Vector<>();
            for (Path e : exps) {
                // collect all export files to a list
                if (Files.isDirectory(e)) {
                    expfiles.addAll(expFiles(e).stream().map(Path::toFile).collect(Collectors.toList()));
                } else if (Files.isReadable(e)) {
                    if (e.toString().endsWith(".exp")) {
                        expfiles.add(e.toFile());
                    } else if (e.toString().endsWith(".jar")) {
                        expfiles.addAll(extractExps(e, tmp).stream().map(Path::toFile).collect(Collectors.toList()));
                    }
                }
            }

            String packagename = cap.getPackageName();
            try (FileInputStream input = new FileInputStream(f.toFile())) {
                // verifyCap takes a FileInputStream up to v3.0.5u1 and a File after that
                try {
                    Method m = verifier.getMethod("verifyCap", File.class, String.class, Vector.class);
                    m.invoke(null, f.toFile(), packagename, expfiles);
                } catch (NoSuchMethodException e) {
                    Method m = verifier.getMethod("verifyCap", FileInputStream.class, String.class, Vector.class);
                    m.invoke(null, input, packagename, expfiles);
                }
            } catch (InvocationTargetException e) {
                Throwable t = e.getTargetException();
                throw new VerifierError(t.getMessage() == null ? t.toString() : t.getMessage(), t);
            } catch (Exception e) {
                throw new VerifierError("Verification failed: " + e.getMessage(), e);
            }
        } catch (ReflectiveOperationException | IOException e) {
            throw new RuntimeException("Could not run verifier: " + e.getMessage(), e);
        } finally {
            capture.close();
            // Clean extracted exps
            rmminusrf(tmp);
        }
    }

    public static void rmminusrf(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(CAPFile::uncheckedDelete);
        } catch (FileNotFoundException | NoSuchFileException e) {
            // Already gone
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Path> expFiles(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".exp"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static Path under(Path out, String name) {
        Path base = out.toAbsolutePath().normalize();
        Path p = base.resolve(name).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException(String.format("Invalid path in JAR: %s vs %s", p, base));
        }
        return p;
    }

    public static List<Path> extractExps(Path jarfilePath, Path out) throws IOException {
        List<Path> exps = new ArrayList<>();
        try (JarFile jarfile = new JarFile(jarfilePath.toFile())) {
            Enumeration<JarEntry> entries = jarfile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().toLowerCase().endsWith(".exp")) {
                    Path f = under(out, entry.getName());
                    Path dir = f.getParent();
                    if (dir == null) {
                        throw new IOException("Null parent"); // spotbugs
                    }
                    if (!Files.isDirectory(dir)) {
                        Files.createDirectories(dir);
                    }
                    try (InputStream is = jarfile.getInputStream(entry);
                         OutputStream fo = Files.newOutputStream(f)) {
                        byte[] buf = new byte[1024];
                        while (true) {
                            int r = is.read(buf);
                            if (r == -1) {
                                break;
                            }
                            fo.write(buf, 0, r);
                        }
                    }
                    exps.add(f);
                }
            }
        }
        return exps;
    }
}
