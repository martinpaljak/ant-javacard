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
import pro.javacard.capfile.HexUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

final class Misc {

    static List<Path> temporary = new ArrayList<>();

    static int getCurrentJDKVersion() {
        String v = System.getProperty("java.version", "0.0.0");
        if (v.startsWith("1.8.")) {
            v = "8." + v.substring(4);
        }
        int dot = v.indexOf(".");
        return Integer.parseInt(v.substring(0, dot == -1 ? v.length() : dot));
    }

    static String hexAID(byte[] aid) {
        StringJoiner hexaid = new StringJoiner(":");
        for (byte b : aid) {
            hexaid.add(String.format("0x%02X", b));
        }
        return hexaid.toString();
    }

    // For cleaning up temporary files
    static void rmminusrf(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (FileNotFoundException | NoSuchFileException e) {
            // Already gone - do nothing.
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] stringToBin(String s) {
        s = s.toLowerCase().replaceAll(" ", "").replaceAll(":", "");
        s = s.replaceAll("0x", "").replaceAll("\n", "").replaceAll("\t", "");
        s = s.replaceAll(";", "");
        return HexUtils.hex2bin(s);
    }

    // foo.bar.Baz -> Baz; Foo -> Foo
    static String lastName(String fqdn) {
        String ln = fqdn;
        if (ln.lastIndexOf(".") != -1) {
            ln = ln.substring(ln.lastIndexOf(".") + 1);
        }
        return ln;
    }

    static Path makeTemp(String sub) {
        try {
            if (System.getenv("ANT_JAVACARD_TMP") != null) {
                Path tmp = Paths.get(System.getenv("ANT_JAVACARD_TMP"), sub);
                // NOTE: would like to make sure that the folder is cleaned, but tmp/imports is shared between
                // all imports and would result in just final import files to survive.
                Files.createDirectories(tmp);
                return tmp;
            } else {
                Path p = Files.createTempDirectory("jccpro");
                temporary.add(p);
                return p;
            }
        } catch (IOException e) {
            throw new RuntimeException("Can not make temporary folder", e);
        }
    }

    static String commonName(CAPFile cap) {
        if (cap.getAppletAIDs().size() == 1 && !cap.getFlags().contains("exports")) {
            String className = cap.getApplets().values().iterator().next();
            if (className != null) {
                return lastName(className);
            }
        }
        return cap.getPackageName();
    }

    static String capFileName(CAPFile cap, String template) {
        return capFileName(cap, template, null);
    }

    static String capFileName(CAPFile cap, String template, String commonNameOverride) {
        String commonName = commonNameOverride == null ? commonName(cap) : lastName(commonNameOverride);
        String hash = HexUtils.bin2hex(cap.getLoadFileDataHash("SHA-256")).toLowerCase();

        String name = template;
        name = name.replace("%H", hash);
        name = name.replace("%h", hash.substring(0, 8));
        name = name.replace("%n", commonName);
        name = name.replace("%p", cap.getPackageName());
        name = name.replace("%a", cap.getPackageAID().toString());
        name = name.replace("%v", "v" + cap.getPackageVersion());
        name = name.replace("%j", cap.guessJavaCardVersion().orElse("unknown"));
        name = name.replace("%g", cap.guessGlobalPlatformVersion().orElse("unknown"));
        name = name.replace("%J", String.format("jdk%d", getCurrentJDKVersion()));
        return name;
    }

    static void cleanTemp() {
        // Do not clean temporary files if manually set temporary path is set. This is useful for debugging.
        if (System.getenv("ANT_JAVACARD_TMP") != null) {
            return;
        }

        if (Boolean.parseBoolean(System.getenv().getOrDefault("_ANT_JAVACARD_LITTER", "false"))) {
            System.err.println("Littering filesystem due to _ANT_JAVACARD_LITTER");
            return;
        }

        // Clean temporary files.
        for (Path f : temporary) {
            if (Files.exists(f)) {
                rmminusrf(f);
            }
        }
    }
}
