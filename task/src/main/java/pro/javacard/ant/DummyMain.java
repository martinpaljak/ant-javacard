// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import pro.javacard.capfile.CAPFile;
import pro.javacard.capfile.HexUtils;
import pro.javacard.sdk.ExportFileHelper;
import pro.javacard.sdk.JavaCardSDK;
import pro.javacard.sdk.OffCardVerifier;
import pro.javacard.sdk.VerifierError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Vector;
import java.util.stream.Collectors;

public final class DummyMain {

    static Path rename(Path path, String template) throws IOException {
        CAPFile cap = CAPFile.fromFile(path);
        boolean isLibrary = cap.getAppletAIDs().isEmpty();
        String effectiveTemplate = template != null ? template
                : isLibrary ? "%n_%a_%v_%h.cap" : "%n_%a_%h_%j.cap";
        Path output = Paths.get(Misc.capFileName(cap, effectiveTemplate));
        if (!path.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            Files.copy(path, output, StandardCopyOption.REPLACE_EXISTING);
        }
        return output;
    }

    static int runcycle(String[] argv) throws IOException {
        Vector<String> args = new Vector<>(Arrays.asList(argv));

        if (args.size() >= 1 && args.get(0).equals("-r")) {
            args.remove(0);
            if (args.isEmpty()) {
                System.err.println("Usage: java -jar ant-javacard.jar -r <capfile>");
                return 1;
            }
            final String capfile = args.remove(0);
            Path path = Paths.get(capfile);
            if (!Files.isRegularFile(path) || !capfile.endsWith(".cap")) {
                System.err.println("Not a valid CAP file: " + capfile);
                return 1;
            }
            try {
                String template = System.getenv("CAP_NAME_TEMPLATE");
                if (template != null && template.contains("%J")) {
                    System.err.println("CAP_NAME_TEMPLATE must not contain %J (JDK version is unknown for rename)");
                    return 1;
                }
                Path output = rename(path, template);
                System.out.println(output);
                if (path.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
                    System.out.println("Already has standard name");
                }
                return 0;
            } catch (Exception e) {
                System.err.println(String.format("Failed to process CAP file: %s: %s", e.getClass().getSimpleName(), e.getMessage()));
                return 1;
            }
        } else if (args.isEmpty()) {
            ProtectionDomain pd = DummyMain.class.getProtectionDomain();
            System.out.println(String.format("This is an ANT task (ant-javacard %s)", DummyMain.class.getPackage().getImplementationVersion()));
            System.out.println("Read usage instructions from https://github.com/martinpaljak/ant-javacard#syntax");

            if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                try {
                    System.out.println();
                    String f = pd.getCodeSource().getLocation().getPath();
                    Path p = Paths.get(f);
                    byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
                    System.out.println(String.format("SHA256 (%s) = %s", f, HexUtils.bin2hex(sha256).toLowerCase()));
                } catch (Exception e) {
                    System.out.println("Could not verify integrity: " + e.getMessage());
                }
            }
            System.out.println();
            System.out.println("But you can use it to dump/verify CAP files, like this:");
            System.out.println("$ java -jar ant-javacard.jar <capfile>");
            System.out.println();
            System.out.println("Or copy a CAP file with a standard name into current directory:");
            System.out.println("$ java -jar ant-javacard.jar -r <capfile>");
            return 1;
        } else if (args.size() == 1) {
            // Simple dumping of capfile
            final String capfile = args.remove(0);

            Path path = Paths.get(capfile);
            if (Files.isRegularFile(path) && capfile.endsWith(".cap")) {
                try {
                    CAPFile cap = CAPFile.fromBytes(Files.readAllBytes(path));
                    cap.dump(System.out);
                    return 0;
                } catch (Exception e) {
                    System.err.println(String.format("Failed to read/parse CAP file: %s: %s", e.getClass().getSimpleName(), e.getMessage()));
                    return 1;
                }
            } else if (Files.isRegularFile(path) && capfile.endsWith(".exp")) {
                try {
                    System.out.println(String.format("%s: %s", path, ExportFileHelper.parsePackage(path)));
                    return 0;
                } catch (Exception e) {
                    System.err.println(String.format("Failed to read/parse EXP file: %s: %s", e.getClass().getSimpleName(), e.getMessage()));
                    return 1;
                }
            } else {
                System.err.println("Usage: java -jar ant-javacard.jar <capfile|expfile>");
                return 1;
            }
        } else {
            // Verification of capfile
            final Path sdkpath = Paths.get(args.remove(0));
            // Targetsdk path is a folder
            final Path targetsdkpath;
            final String capfile;
            final String next = args.remove(0);
            if (Files.isDirectory(Paths.get(next))) {
                targetsdkpath = Paths.get(next);
                capfile = args.remove(0);
            } else {
                capfile = next;
                targetsdkpath = sdkpath;
            }
            // If jarfile is given, exports from jar files are extracted internally.
            Vector<File> exps = args.stream().map(File::new).collect(Collectors.toCollection(Vector::new));

            CAPFile cap = CAPFile.fromBytes(Files.readAllBytes(Paths.get(capfile)));
            try {
                JavaCardSDK sdk = JavaCardSDK.detectSDK(sdkpath).orElseThrow(() -> new VerifierError("No SDK detected in " + sdkpath));
                JavaCardSDK target = JavaCardSDK.detectSDK(targetsdkpath).orElseThrow(() -> new VerifierError("No target SDK detected with " + targetsdkpath));

                OffCardVerifier verifier = OffCardVerifier.withSDK(sdk);

                cap.dump(System.out);

                verifier.verifyAgainst(new File(capfile), target, exps);
                System.out.println(String.format("Verified %s with SDK v%s against SDK v%s", capfile, sdk.getVersion(), target.getVersion()));
                return 0;
            } catch (VerifierError e) {
                System.err.println("Verification failed: " + e.getMessage());
                return 1;
            }
        }
    }

    public static void main(String[] argv) {
        try {
            runcycle(argv);
        } catch (Throwable e) {
            System.err.println(String.format("Error: %s: %s", e.getClass().getSimpleName(), e.getMessage()));
            if (System.getenv("ANT_JAVACARD_DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

}
