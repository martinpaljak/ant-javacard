/*
 * Copyright (c) 2015-2024 Martin Paljak
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pro.javacard.ant;

import pro.javacard.capfile.CAPFile;
import pro.javacard.sdk.ExportFileHelper;
import pro.javacard.sdk.JavaCardSDK;
import pro.javacard.sdk.OffCardVerifier;
import pro.javacard.sdk.VerifierError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Vector;
import java.util.stream.Collectors;

public final class DummyMain {

    static int runcycle(String[] argv) throws IOException {
        Vector<String> args = new Vector<>(Arrays.asList(argv));

        if (args.isEmpty()) {
            System.out.println("This is an ANT task (ant-javacard " + DummyMain.class.getPackage().getImplementationVersion() + ")");
            System.out.println("Read usage instructions from https://github.com/martinpaljak/ant-javacard#syntax");
            System.out.println();
            System.out.println("But you can use it to dump/verify CAP files, like this:");
            System.out.println("$ java -jar ant-javacard.jar <capfile>");
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
                    System.err.printf("Failed to read/parse CAP file: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
                    return 1;
                }
            } else if (Files.isRegularFile(path) && capfile.endsWith(".exp")) {
                try {
                    System.out.printf("%s: %s%n", path, ExportFileHelper.getVersion(path).get());
                    return 0;
                } catch (Exception e) {
                    System.err.printf("Failed to read/parse EXP file: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
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
                System.out.printf("Verified %s with SDK v%s against SDK v%s%n", capfile, sdk.getVersion(), target.getVersion());
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
            Misc.cleanTemp();
            System.err.printf("Error: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            if (System.getenv("ANT_JAVACARD_DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}
