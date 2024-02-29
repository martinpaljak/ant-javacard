/**
 * Copyright (c) 2015-2023 Martin Paljak
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
    public static void main(String[] argv) {
        try {
            Vector<String> args = new Vector<>(Arrays.asList(argv));
            System.out.println("args: " + args);
            if (args.isEmpty()) {
                System.out.println("This is an ANT task, not a program!");
                System.out.println("Read usage instructions from https://github.com/martinpaljak/ant-javacard#syntax");
                System.out.println();
                System.out.println("But you can use it to dump/verify CAP files, like this:");
                System.exit(1);
            } else if (args.size() == 1) {
                final String capfile = args.remove(0);
                if (Files.isRegularFile(Paths.get(capfile)) && capfile.endsWith(".cap")) {
                    try {
                        CAPFile cap = CAPFile.fromBytes(Files.readAllBytes(Paths.get(capfile)));
                        cap.dump(System.out);
                    } catch (Exception e) {
                        System.err.printf("Failed to read/parse CAP file: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
                        System.exit(1);
                    }
                } else {
                    System.err.println("Usage: java -jar ant-javacard.jar <capfile>");
                    System.exit(1);
                }
            } else {
                final Path sdkpath = Paths.get(args.remove(0));
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
                Vector<File> exps = new Vector<>(args.stream().map(i -> new File(i)).collect(Collectors.toList()));
                CAPFile cap = CAPFile.fromBytes(Files.readAllBytes(Paths.get(capfile)));
                cap.dump(System.out);
                try {
                    JavaCardSDK sdk = JavaCardSDK.detectSDK(sdkpath).orElseThrow(() -> new VerifierError("No SDK detected in " + sdkpath));
                    JavaCardSDK target = JavaCardSDK.detectSDK(targetsdkpath).orElseThrow(() -> new VerifierError("No target SDK detected with " + targetsdkpath));

                    OffCardVerifier verifier = OffCardVerifier.withSDK(sdk);
                    verifier.verifyAgainst(new File(capfile), target, exps);
                    System.out.printf("Verified %s with SDK v%s against SDK v%s%n", capfile, sdk.getVersion(), target.getVersion());
                } catch (VerifierError e) {
                    System.err.println("Verification failed: " + e.getMessage());
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.printf("Error: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
}
