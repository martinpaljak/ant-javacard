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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JavaCardSDK {

    private static final Pattern COPYRIGHT_YEAR = Pattern.compile("(\\d{4}), Oracle");

    public static Optional<JavaCardSDK> detectSDK(Path path) {
        if (path == null) {
            throw new NullPointerException("path is null");
        }

        SDKRelease release = detectRelease(path);

        if (release == null) {
            return Optional.empty();
        }

        return Optional.of(new JavaCardSDK(path, release));
    }

    private static Optional<Properties> toolsVersion(Path root) {
        Path tools = root.resolve("lib").resolve("tools.jar");
        if (!Files.exists(tools)) {
            return Optional.empty();
        }
        try (ZipFile toolsZip = new ZipFile(tools.toFile())) {
            ZipEntry toolsver = toolsZip.getEntry("com/sun/javacard/toolsversion.properties");
            if (toolsver == null) {
                return Optional.empty();
            }
            Properties verprop = new Properties();
            verprop.load(toolsZip.getInputStream(toolsver));
            return Optional.of(verprop);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static SDKRelease detect305(Properties toolsver) {
        Matcher year = COPYRIGHT_YEAR.matcher(toolsver.getProperty("copyright.banner", ""));
        if (year.find()) {
            switch (year.group(1)) {
                case "2015":
                    return SDKRelease.V305u1;
                case "2017":
                    return SDKRelease.V305u2;
                case "2018":
                    return SDKRelease.V305u3;
                case "2020":
                    return SDKRelease.V305u4;
                default:
                    return SDKRelease.V305;
            }
        }
        return SDKRelease.V305;
    }

    private static SDKRelease detectRelease(Path root) {
        SDKRelease release = null;
        Path libDir = root.resolve("lib");
        Optional<Properties> toolsver = toolsVersion(root);
        if (toolsver.isPresent()) {
            String ver = toolsver.get().getProperty("converter.version");
            // A tools.jar that names no converter version is not an SDK
            if (ver == null) {
                return null;
            }
            switch (ver) {
                case "3.0.3":
                    return SDKRelease.V301; // XXX: SDK v3.0.3 filed as v3.0.1
                case "3.0.4":
                    return SDKRelease.V304;
                case "3.0.5":
                    return detect305(toolsver.get());
                case "3.1.0":
                    return SDKRelease.V310;
                case "3.2.0":
                    return SDKRelease.V24_0;
                case "24.1":
                    return SDKRelease.V24_1;
                case "25.0":
                    return SDKRelease.V25_0;
                case "25.1":
                    return SDKRelease.V25_1;
                case "26.0":
                    return SDKRelease.V26_0;
                default:
                    throw new IllegalArgumentException(String.format("Unsupported JavaCard SDK in %s (converter.version %s): upgrade ant-javacard or use a supported SDK", root, ver));
            }
        } else if (Files.exists(libDir.resolve("api21.jar"))) {
            release = SDKRelease.V212;
        } else if (Files.exists(root.resolve("bin").resolve("api.jar"))) {
            release = SDKRelease.V211;
        } else if (Files.exists(libDir.resolve("converter.jar"))) {
            release = SDKRelease.V221;
            // javacardx.apdu.ExtendedLength first appears in API 2.2.2
            Path api = libDir.resolve("api.jar");
            try (ZipFile apiZip = new ZipFile(api.toFile())) {
                ZipEntry testEntry = apiZip.getEntry("javacardx/apdu/ExtendedLength.class");
                if (testEntry != null) {
                    release = SDKRelease.V222;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return release;
    }


    // Where an SDK keeps the export files of the API version it ships
    private static Path exportDir(APIVersion api) {
        switch (api) {
            case V212:
                return Paths.get("api21_export_files");
            case V310:
            case V320:
            case PREVIEW:
            case PREVIEW_FINAL:
                return Paths.get("api_export_files_" + api.v);
            default:
                return Paths.get("api_export_files");
        }
    }

    // Where a multi-target SDK keeps the export files of an API version it only targets
    private static Path targetExportDir(APIVersion api) {
        return Paths.get("api_export_files_" + api.v);
    }

    private static Optional<Path> annotationsJar(APIVersion api) {
        if (api.isOneOf(APIVersion.V304, APIVersion.V305)) {
            return Optional.of(Paths.get("lib", "api_classic_annotations.jar"));
        }
        if (api.equalOrNewer(APIVersion.V310)) {
            return Optional.of(Paths.get("lib", String.format("api_classic_annotations-%s.jar", api.v)));
        }
        return Optional.empty();
    }

    private static List<Path> apiJars(APIVersion api) {
        List<Path> jars = new ArrayList<>();
        switch (api) {
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
            default:
                jars.add(Paths.get("lib", String.format("api_classic-%s.jar", api.v)));
                break;
        }
        annotationsJar(api).ifPresent(jars::add);
        return jars;
    }

    private static List<Path> toolJars(SDKRelease release) {
        List<Path> jars = new ArrayList<>();
        if (release == SDKRelease.V211) {
            jars.add(Paths.get("bin", "converter.jar"));
        } else if (release.equalOrNewer(SDKRelease.V301)) {
            jars.add(Paths.get("lib", "tools.jar"));
        } else {
            jars.add(Paths.get("lib", "converter.jar"));
            jars.add(Paths.get("lib", "offcardverifier.jar"));
        }
        return jars;
    }

    // What javac needs to run the annotation processor of the SDK
    private static List<Path> compilerJars(SDKRelease release) {
        List<Path> jars = new ArrayList<>();
        Optional<Path> annotations = annotationsJar(release.api());
        if (annotations.isPresent()) {
            jars.add(Paths.get("lib", "tools.jar"));
            jars.add(annotations.get());
        }
        return jars;
    }

    public String converterClass() {
        return release.equalOrNewer(SDKRelease.V301) ? "com.sun.javacard.converter.Main" : "com.sun.javacard.converter.Converter";
    }

    private final Path path;
    private final SDKRelease release;

    private JavaCardSDK(Path root, SDKRelease release) {
        this.path = root;
        this.release = release;
    }

    private List<Path> under(List<Path> relative) {
        return relative.stream().map(path::resolve).collect(Collectors.toList());
    }

    public Path getRoot() {
        return path;
    }

    public SDKRelease getRelease() {
        return release;
    }

    public List<Path> getCompilerJars() {
        return under(compilerJars(release));
    }

    public List<Path> getToolJars() {
        return under(toolJars(release));
    }

    // The API this SDK ships
    public JavaCardAPI api() {
        APIVersion own = release.api();
        return new JavaCardAPI(this, own, under(apiJars(own)), path.resolve(exportDir(own)));
    }

    // Every API version this SDK can build against
    public Set<APIVersion> provides() {
        Set<APIVersion> all = EnumSet.of(release.api());
        all.addAll(release.targets());
        return all;
    }

    public Optional<JavaCardAPI> api(APIVersion version) {
        if (version == release.api()) {
            return Optional.of(api());
        }
        if (!release.targets().contains(version)) {
            return Optional.empty();
        }
        List<Path> jars = new ArrayList<>();
        jars.add(Paths.get("lib", String.format("api_classic-%s.jar", version.v)));
        jars.add(Paths.get("lib", String.format("api_classic_annotations-%s.jar", version.v)));
        return Optional.of(new JavaCardAPI(this, version, under(jars), path.resolve(targetExportDir(version))));
    }

    // The caller closes the returned loader
    public URLClassLoader getClassLoader() {
        String jar = release.equalOrNewer(SDKRelease.V301) ? "tools.jar" : "offcardverifier.jar";
        try {
            URL url = path.resolve("lib").resolve(jar).toUri().toURL();
            return new URLClassLoader(new URL[]{url}, JavaCardSDK.class.getClassLoader());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid SDK path: " + e.getMessage());
        }
    }

    // Relative export folder paths of every API version an SDK provides
    public static List<Path> getAllExportDirs(SDKRelease release) {
        ArrayList<Path> dirs = new ArrayList<>();
        dirs.add(exportDir(release.api()));
        for (APIVersion target : release.targets()) {
            dirs.add(targetExportDir(target));
        }
        return dirs;
    }
}
