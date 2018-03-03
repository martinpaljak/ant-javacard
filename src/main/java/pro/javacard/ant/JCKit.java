package pro.javacard.ant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JCKit {

    public static JCKit detectSDK(String path) {
        if (path == null || path.trim() == "") {
            return null;
        }

        File root = new File(path);
        if(!root.isDirectory()) {
            return null;
        }

        Version version = detectSDKVersion(root);
        if(version == null) {
            return null;
        }

        return new JCKit(root, version);
    }

    private static Version detectSDKVersion(File root) {
        Version version = null;
        File libDir = new File(root, "lib");
        if (new File(libDir, "tools.jar").exists()) {
            version = JCKit.Version.V3;
        } else if (new File(libDir, "api21.jar").exists()) {
            version = JCKit.Version.V21;
        } else if (new File(libDir, "converter.jar").exists()) {
            // assume 2.2.1 first
            version = Version.V221;
            // test for 2.2.2 by testing api.jar
            File api = new File(libDir, "api.jar");
            try {
                ZipFile apiZip = new ZipFile(api);
                ZipEntry testEntry = apiZip.getEntry("javacardx/apdu/ExtendedLength.class");
                if(testEntry != null) {
                    version = JCKit.Version.V222;
                }
            } catch (IOException ignored) {
                // do not ignore this, escalate it
                throw new RuntimeException(ignored);
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
        switch(version) {
            case V3:
                return "1.5";
            case V222:
                return "1.3";
            case V221:
                return "1.2";
            case V21:
            default:
                return "1.1";
        }
    }

    public File getJar(String name) {
        File libDir = new File(path, "lib");
        return new File(libDir, name);
    }

    public File getApiJar() {
        switch (version) {
            case V21:
                return getJar("api21.jar");
            case V3:
                return getJar("api_classic.jar");
            default:
                return getJar("api.jar");
        }
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
        switch (version) {
            case V3:
                jars.add(getJar("tools.jar"));
                break;
            default:
                jars.add(getJar("converter.jar"));
                jars.add(getJar("offcardverifier.jar"));
                break;
        }
        return jars;
    }

    enum Version {
        NONE, V21, V221, V222, V3;

        @Override
        public String toString() {
            if (this.equals(V3))
                return "v3.x";
            if (this.equals(V222))
                return "v2.2.2";
            if (this.equals(V221))
                return "v2.2.1";
            if (this.equals(V21))
                return "v2.1.x";
            return "unknown";
        }
    }

}
