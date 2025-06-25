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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

final class Misc {

    // This code has been taken from Apache commons-codec 1.7 (License: Apache 2.0)
    private static final char[] LOWER_HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    static List<Path> temporary = new ArrayList<>();

    static String encodeHexString(final byte[] data) {
        final int l = data.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = LOWER_HEX[(0xF0 & data[i]) >>> 4];
            out[j++] = LOWER_HEX[0x0F & data[i]];
        }
        return new String(out);
    }

    static byte[] decodeHexString(String str) {
        char data[] = str.toCharArray();
        final int len = data.length;
        if ((len & 0x01) != 0) {
            throw new IllegalArgumentException("Odd number of characters: " + str);
        }
        final byte[] out = new byte[len >> 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; j < len; i++) {
            int f = Character.digit(data[j], 16) << 4;
            j++;
            f = f | Character.digit(data[j], 16);
            j++;
            out[i] = (byte) (f & 0xFF);
        }
        return out;
    }


    // Dirty way to get major version of JDK: 8, 11, 17 etc
    static int getCurrentJDKVersion() {
        String v = System.getProperty("java.version", "0.0.0");
        if (v.startsWith("1.8."))
            v = "8." + v.substring(4);
        int dot = v.indexOf(".");
        int m = Integer.parseInt(v.substring(0, dot == -1 ? v.length() : dot));
        return m;
    }

    static String hexAID(byte[] aid) {
        StringBuffer hexaid = new StringBuffer();
        for (byte b : aid) {
            hexaid.append(String.format("0x%02X", b));
            hexaid.append(":");
        }
        String hex = hexaid.toString();
        // Cut off the final colon
        return hex.substring(0, hex.length() - 1);
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
        return decodeHexString(s);
    }

    // foo.bar.Baz -> Baz; Foo -> Foo
    static String className(String fqdn) {
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
