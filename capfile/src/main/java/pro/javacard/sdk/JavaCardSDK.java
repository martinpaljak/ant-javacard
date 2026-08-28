// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JavaCardSDK {

    // Oracle stamps the tools with the year the kit was built, which is all that separates the 3.0.5 updates
    private static final Pattern COPYRIGHT_YEAR = Pattern.compile("(\\d{4}), Oracle");

    public static Optional<JavaCardSDK> detectSDK(Path path) {
        if (path == null) {
            throw new NullPointerException("path is null");
        }

        // Detect
        SDKVersion version = detectSDKVersion(path);

        if (version == null) {
            return Optional.empty();
        }

        Path exportDir = getExportDir(version);
        List<Path> apiJars = getApiJars(version);
        List<Path> compilerJars = getCompilerJars(version);
        List<Path> toolJars = getToolJars(version);

        JavaCardSDK sdk = new JavaCardSDK(path, version, exportDir, apiJars, toolJars, compilerJars);
        return Optional.of(sdk);
    }

    // Tool versions and copyright banner of a 3.x kit, empty for older kits
    private static Optional<Properties> toolsVersion(Path root) {
        Path tools = root.resolve("lib").resolve("tools.jar");
        if (!Files.exists(tools))
            return Optional.empty();
        try (ZipFile toolsZip = new ZipFile(tools.toFile())) {
            ZipEntry toolsver = toolsZip.getEntry("com/sun/javacard/toolsversion.properties");
            if (toolsver == null)
                return Optional.empty();
            Properties verprop = new Properties();
            verprop.load(toolsZip.getInputStream(toolsver));
            return Optional.of(verprop);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SDKVersion detectSDKVersion(Path root) {
        SDKVersion version = null;
        Path libDir = root.resolve("lib");
        Optional<Properties> toolsver = toolsVersion(root);
        if (toolsver.isPresent()) {
            String ver = toolsver.get().getProperty("converter.version");
            switch (ver) {
                case "3.0.3":
                    return SDKVersion.V301; // XXX
                case "3.0.4":
                    return SDKVersion.V304;
                case "3.0.5":
                    return SDKVersion.V305;
                case "3.1.0":
                    return SDKVersion.V310;
                case "3.2.0":
                    return SDKVersion.V320; // 24.0
                case "24.1":
                    return SDKVersion.V320_24_1;
                case "25.0":
                    return SDKVersion.V320_25_0;
                case "25.1":
                    return SDKVersion.V320_25_1;
                case "26.0":
                    return SDKVersion.V320_26_0;
                default:
                    throw new IllegalStateException("Unknown SDK release: " + ver);
            }
        } else if (Files.exists(libDir.resolve("api21.jar"))) {
            version = SDKVersion.V212;
        } else if (Files.exists(root.resolve("bin").resolve("api.jar"))) {
            version = SDKVersion.V211;
        } else if (Files.exists(libDir.resolve("converter.jar"))) {
            // assume 2.2.1 first
            version = SDKVersion.V221;
            // test for 2.2.2 by testing api.jar
            Path api = libDir.resolve("api.jar");
            try (ZipFile apiZip = new ZipFile(api.toFile())) {
                ZipEntry testEntry = apiZip.getEntry("javacardx/apdu/ExtendedLength.class");
                if (testEntry != null) {
                    version = SDKVersion.V222;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return version;
    }

    private final Path path;
    private final SDKVersion version;

    private final Path exportDir;
    private final List<Path> apiJars;
    private final List<Path> toolJars;
    private final List<Path> compilerJars;

    private JavaCardSDK(Path root, SDKVersion version, Path exportDir, List<Path> apiJars, List<Path> toolJars, List<Path> compilerJars) {
        this.path = root;
        this.version = version;

        this.exportDir = path.resolve(exportDir);
        this.apiJars = apiJars.stream().map(path::resolve).collect(Collectors.toList());
        this.compilerJars = compilerJars.stream().map(path::resolve).collect(Collectors.toList());
        this.toolJars = toolJars.stream().map(path::resolve).collect(Collectors.toList());
    }

    public Path getRoot() {
        return path;
    }

    public SDKVersion getVersion() {
        return version;
    }

    public List<Path> getApiJars() {
        return Collections.unmodifiableList(apiJars);
    }

    public List<Path> getCompilerJars() {
        return Collections.unmodifiableList(compilerJars);
    }

    public List<Path> getToolJars() {
        return Collections.unmodifiableList(toolJars);
    }

    public Path getExportDir() {
        return exportDir;
    }

    // This is for build and verification tools
    public JavaCardSDK target(SDKVersion targetVersion) {
        if (version.targets.contains(targetVersion)) {
            List<Path> apiJars = new ArrayList<>();
            apiJars.add(Paths.get("lib", String.format("api_classic-%s.jar", targetVersion.v)));
            apiJars.add(Paths.get("lib", String.format("api_classic_annotations-%s.jar", targetVersion.v)));
            Path exportPath = Paths.get(String.format("api_export_files_%s", targetVersion.v));
            return new JavaCardSDK(path, targetVersion, exportPath, apiJars, toolJars, compilerJars);
        } else {
            throw new IllegalArgumentException(String.format("Can not target %s with %s", targetVersion, version));
        }
    }

    // Compared by version string, not by constant: 3.2.0 names the 24.0 through 26.0 kits alike
    public Optional<JavaCardSDK> targeting(SDKVersion targetVersion) {
        if (version.toString().equals(targetVersion.toString())) {
            return Optional.empty();
        }
        if (version.targets().contains(targetVersion)) {
            return Optional.of(target(targetVersion));
        }
        throw new IllegalArgumentException(String.format("JavaCard kit v%s (JavaCard %s) can not target JavaCard %s, only %s",
                getRelease(), version, targetVersion, version.targets().stream().sorted().map(Object::toString).collect(Collectors.joining(", "))));
    }

    // Returns the classloader of verifier
    @SuppressWarnings("removal") // AccessController
    public ClassLoader getClassLoader() {
        return java.security.AccessController.doPrivileged(new PrivilegedAction<URLClassLoader>() {
            public URLClassLoader run() {
                try {
                    if (version.equalOrNewer(SDKVersion.V301)) {
                        return new URLClassLoader(new URL[]{path.resolve("lib").resolve("tools.jar").toUri().toURL()}, this.getClass().getClassLoader());
                    } else {
                        return new URLClassLoader(new URL[]{path.resolve("lib").resolve("offcardverifier.jar").toUri().toURL()}, this.getClass().getClassLoader());
                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException("Could not load classes: " + e.getMessage());
                }
            }
        });
    }

    public String getRelease() {
        switch (version) {
            case V305:
                // Every 3.0.5 update reports converter version 3.0.5; the copyright year of the tools tells them apart
                Matcher year = COPYRIGHT_YEAR.matcher(toolsVersion(path).map(p -> p.getProperty("copyright.banner", "")).orElse(""));
                if (year.find()) {
                    switch (year.group(1)) {
                        case "2015":
                            return "3.0.5u1";
                        case "2017":
                            return "3.0.5u2";
                        case "2018":
                            return "3.0.5u3";
                        case "2020":
                            return "3.0.5u4";
                    }
                }
                return "3.0.5";
            case V320:
                return "24.0";
            case V320_24_1:
                return "24.1";
            case V320_25_0:
                return "25.0";
            case V320_25_1:
                return "25.1";
            case V320_26_0:
                return "26.0";
            default:
                return version.toString();
        }
    }

    // All export dir paths an SDK provides: its own + one per target version
    public static List<Path> getAllExportDirs(SDKVersion version) {
        ArrayList<Path> dirs = new ArrayList<Path>();
        dirs.add(getExportDir(version));
        for (SDKVersion target : version.targets) {
            dirs.add(Paths.get("api_export_files_" + target.v));
        }
        return dirs;
    }

    public static Path getExportDir(SDKVersion version) {
        switch (version) {
            case V212:
                return Paths.get("api21_export_files");
            case V310:
            case V320:
            case V320_24_1:
            case V320_25_0:
            case V320_25_1:
            case V320_26_0:
                return Paths.get("api_export_files_" + version.v);
            default:
                return Paths.get("api_export_files");
        }
    }

    public static List<Path> getApiJars(SDKVersion version) {
        List<Path> jars = new ArrayList<>();
        switch (version) {
            case V211:
                jars.add(Paths.get("bin", "api.jar"));
                break;
            case V212:
                jars.add(Paths.get("lib", "api21.jar"));
                break;
            case V221:
            case V222:
                jars.add(Paths.get("lib", "api.jar"));
                break;
            case V301:
            case V304:
            case V305:
                jars.add(Paths.get("lib", "api_classic.jar"));
                break;
            case V310:
            case V320:
            case V320_24_1:
            case V320_25_0:
            case V320_25_1:
            case V320_26_0:
                jars.add(Paths.get("lib", String.format("api_classic-%s.jar", version.v)));
                jars.add(Paths.get("lib", String.format("api_classic_annotations-%s.jar", version.v)));
                break;
            default:
                throw new IllegalStateException("Unknown SDK: " + version);
        }
        // Add annotations for 3.0.4 and 3.0.5
        if (version.isOneOf(SDKVersion.V304, SDKVersion.V305)) {
            jars.add(Paths.get("lib", "api_classic_annotations.jar"));
        }
        return jars;
    }

    public static List<Path> getToolJars(SDKVersion version) {
        List<Path> jars = new ArrayList<>();
        if (version.isOneOf(SDKVersion.V211)) {
            // We don't support verification with 2.1.X, so only converter
            jars.add(Paths.get("bin", "converter.jar"));
        } else if (version.equalOrNewer(SDKVersion.V301)) {
            jars.add(Paths.get("lib", "tools.jar"));
        } else {
            jars.add(Paths.get("lib", "converter.jar"));
            jars.add(Paths.get("lib", "offcardverifier.jar"));
        }
        return jars;
    }

    public static List<Path> getCompilerJars(SDKVersion version) {
        List<Path> jars = new ArrayList<>();
        if (version.isOneOf(SDKVersion.V304, SDKVersion.V305)) {
            jars.add(Paths.get("lib", "tools.jar"));
            jars.add(Paths.get("lib", "api_classic_annotations.jar"));
        } else if (version.equalOrNewer(SDKVersion.V310)) {
            jars.add(Paths.get("lib", "tools.jar"));
            jars.add(Paths.get("lib", String.format("api_classic_annotations-%s.jar", version.v)));
        }
        return jars;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JavaCardSDK) {
            JavaCardSDK other = (JavaCardSDK) o;
            return path.toAbsolutePath().equals(other.path.toAbsolutePath()) && version.equals(other.version) && exportDir.equals(other.exportDir);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, exportDir);
    }
}
