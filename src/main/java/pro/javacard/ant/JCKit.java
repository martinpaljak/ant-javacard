/**
 * Copyright (c) 2015-2018 Martin Paljak
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JCKit {

    public static JCKit detectSDK(String path) {
        if (path == null || path.trim().length() == 0) {
            return null;
        }

        File root = new File(path);
        if (!root.isDirectory()) {
            return null;
        }

        Version version = detectSDKVersion(root);
        if (version == null) {
            return null;
        }

        return new JCKit(root, version);
    }

    private static Version detectSDKVersion(File root) {
        Version version = null;
        File libDir = new File(root, "lib");
        if (new File(libDir, "tools.jar").exists()) {
            File api = new File(libDir, "api_classic.jar");
            try (ZipFile apiZip = new ZipFile(api)) {
                if (apiZip.getEntry("javacardx/framework/SensitiveArrays.class") != null) {
                    return Version.V305;
                }
                if (apiZip.getEntry("javacardx/framework/string/StringUtil.class") != null) {
                    return Version.V304;
                }
                return Version.V301;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (new File(libDir, "api21.jar").exists()) {
            version = JCKit.Version.V21;
        } else if (new File(libDir, "converter.jar").exists()) {
            // assume 2.2.1 first
            version = Version.V221;
            // test for 2.2.2 by testing api.jar
            File api = new File(libDir, "api.jar");
            try (ZipFile apiZip = new ZipFile(api)) {
                ZipEntry testEntry = apiZip.getEntry("javacardx/apdu/ExtendedLength.class");
                if (testEntry != null) {
                    version = JCKit.Version.V222;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return version;
    }

    private Version version = Version.NONE;
    private File path = null;

    public JCKit(File root, Version version) {
        this.path = root;
        this.version = version;
    }

    public File getRoot() {
        return path;
    }

    public Version getVersion() {
        return version;
    }

    public boolean isVersion(Version v) {
        return version.equals(v);
    }

    public String getJavaVersion() {
        switch (version) {
            case V301:
            case V304:
            case V305:
                return "1.6";
            case V222:
                return "1.5";
            case V221:
                return "1.2";
            default:
                return "1.1";
        }
    }

    public File getJar(String name) {
        File libDir = new File(path, "lib");
        return new File(libDir, name);
    }

    public List<File> getApiJars() {
        List<File> jars = new ArrayList<>();
        switch (version) {
            case V21:
                jars.add(getJar("api21.jar"));
                break;
            case V301:
            case V304:
            case V305:
                jars.add(getJar("api_classic.jar"));
                break;
            default:
                jars.add(getJar("api.jar"));
        }
        // Add annotations
        if (version == Version.V304 || version == Version.V305) {
            jars.add(getJar("api_classic_annotations.jar"));
        }
        return jars;
    }

    public File getExportDir() {
        switch (version) {
            case V21:
                return new File(path, "api21_export_files");
            default:
                return new File(path, "api_export_files");
        }
    }

    public List<File> getToolJars() {
        List<File> jars = new ArrayList<>();
        if (version.isV3()) {
            jars.add(getJar("tools.jar"));
        } else {
            jars.add(getJar("converter.jar"));
            jars.add(getJar("offcardverifier.jar"));
        }
        return jars;
    }

    public List<File> getCompilerJars() {
        List<File> jars = new ArrayList<>();
        if (version == Version.V304) {
            jars.add(getJar("tools.jar"));
            jars.add(getJar("api_classic_annotations.jar"));
        }
        return jars;
    }

    enum Version {
        NONE, V21, V221, V222, V301, V304, V305;

        @Override
        public String toString() {
            if (this.equals(V305))
                return "v3.0.5";
            if (this.equals(V304))
                return "v3.0.4";
            if (this.equals(V301))
                return "v3.0.1";
            if (this.equals(V222))
                return "v2.2.2";
            if (this.equals(V221))
                return "v2.2.1";
            if (this.equals(V21))
                return "v2.1.1";
            return "unknown";
        }

        public boolean isV3() {
            switch (this) {
                case V301:
                case V304:
                case V305:
                    return true;
                default:
                    return false;
            }
        }
    }

}
