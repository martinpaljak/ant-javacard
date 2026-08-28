// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import pro.javacard.capfile.CAPFile;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static pro.javacard.sdk.SDKVersion.*;

public final class OffCardVerifier {
    private final JavaCardSDK sdk;

    public static OffCardVerifier withSDK(JavaCardSDK sdk) {
        // Only main method in 2.1 SDK
        if (sdk.getVersion().isOneOf(V211, V212)) {
            throw new RuntimeException("Verification is supported with JavaCard SDK 2.2.1 or later");
        }
        return new OffCardVerifier(sdk);
    }

    private OffCardVerifier(JavaCardSDK sdk) {
        this.sdk = sdk;
    }

    // Verify a CAP file against a specific JavaCard target SDK and a set of EXP files
    public void verifyAgainst(File f, JavaCardSDK target, Vector<File> exps) throws VerifierError, IOException {
        List<Path> exports = new ArrayList<>(exps.stream().map(File::toPath).collect(Collectors.toList()));
        exports.add(target.getExportDir());
        verify(f.toPath(), exports);
    }

    public void verifyAgainst(Path f, JavaCardSDK target, List<Path> exps) throws VerifierError, IOException {
        // Warn about recommended usage
        if (target.getVersion().isOneOf(V304, V305, V310) && sdk.getVersion() != V320_25_0) {
            System.err.println("NB! Please use JavaCard SDK 3.2.0 / 25.0 for verifying!");
        } else {
            if (!sdk.getRelease().equals("3.0.5u4")) {
                System.err.println("NB! Please use JavaCard SDK 3.0.5u4 or later for verifying!");
            }
        }
        List<Path> exports = new ArrayList<>(exps.stream().collect(Collectors.toList()));
        exports.add(target.getExportDir());
        verify(f, exports);
    }

    // Verify a given CAP file against a set of EXP files
    public void verify(Path f, List<Path> exps) throws VerifierError, IOException {
        Path tmp = Files.createTempDirectory("capfile");
        try (InputStream in = Files.newInputStream(f)) {
            CAPFile cap = CAPFile.fromStream(in);

            // Get verifier class
            Class<?> verifier = Class.forName("com.sun.javacard.offcardverifier.Verifier", true, sdk.getClassLoader());

            // Verifier takes a vector of files, so collect
            final Vector<File> expfiles = new Vector<>();
            for (Path e : exps) {
                // collect all export files to a list
                if (Files.isDirectory(e)) {
                    expfiles.addAll(Files.walk(e.toRealPath()).filter(p -> p.toString().endsWith(".exp")).map(Path::toFile).collect(Collectors.toList()));
                } else if (Files.isReadable(e)) {
                    if (e.toString().endsWith(".exp")) {
                        expfiles.add(e.toFile());
                    } else if (e.toString().endsWith(".jar")) {
                        expfiles.addAll(extractExps(e, tmp).stream().map(Path::toFile).collect(Collectors.toList()));
                    }
                }
            }

            String packagename = cap.getPackageName();
            // XXX: calling this on SDK 25.0 would set the level from INFO to ALL, so manually revert it in finally
            Level logger_before = Logger.getLogger("").getLevel();
            try (FileInputStream input = new FileInputStream(f.toFile())) {
                // Kits up to 3.0.5u1 take the open stream, later ones take the file
                try {
                    Method m = verifier.getMethod("verifyCap", File.class, String.class, Vector.class);
                    m.invoke(null, f.toFile(), packagename, expfiles);
                } catch (NoSuchMethodException e) {
                    Method m = verifier.getMethod("verifyCap", FileInputStream.class, String.class, Vector.class);
                    m.invoke(null, input, packagename, expfiles);
                }
            } catch (InvocationTargetException e) {
                throw new VerifierError(e.getTargetException().getMessage(), e.getTargetException());
            } catch (Exception e) {
                throw new VerifierError("Verification failed: " + e.getMessage(), e);
            } finally {
                Level logger_now = Logger.getLogger("").getLevel();
                if (!logger_before.equals(logger_now)) {
                    System.err.println(String.format("Resetting root logger from %s back to %s", logger_now, logger_before));
                    Logger.getLogger("").setLevel(logger_before);
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            throw new RuntimeException("Could not run verifier: " + e.getMessage(), e);
        } finally {
            // Clean extracted exps
            rmminusrf(tmp);
        }
    }

    private static void rmminusrf(Path path) {
        try {
            Files.walk(path).sorted(Comparator.reverseOrder()).forEach(CAPFile::uncheckedDelete);
        } catch (FileNotFoundException | NoSuchFileException e) {
            // Already gone - do nothing.
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path under(Path out, String name) {
        Path p = out.resolve(name).normalize().toAbsolutePath();
        if (!p.startsWith(out)) {
            throw new IllegalArgumentException(String.format("Invalid path in JAR: %s vs %s", p, out));
        }
        return p;
    }

    // Extracts .exp files from a jarfile to given path (temp folder) and returns the list of .exp files there
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
                        //      throw new IOException("Failed to create folder: " + f.getParentFile());
                        // f = under(out, entry.getName());
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
