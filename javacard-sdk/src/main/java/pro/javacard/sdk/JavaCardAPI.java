// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// One JavaCard API version from one SDK: the classes to compile against and the export files to link against.
public final class JavaCardAPI {
    private final JavaCardSDK sdk;
    private final APIVersion version;
    private final List<Path> apiJars;
    private final Path exportDir;

    JavaCardAPI(JavaCardSDK sdk, APIVersion version, List<Path> apiJars, Path exportDir) {
        this.sdk = sdk;
        this.version = version;
        this.apiJars = apiJars;
        this.exportDir = exportDir;
    }

    public APIVersion version() {
        return version;
    }

    // The converter may come from another SDK
    public JavaCardSDK sdk() {
        return sdk;
    }

    // The JavaCard API jars to compile an applet against
    public List<Path> apiJars() {
        return Collections.unmodifiableList(apiJars);
    }

    // Empty for the SDK-s that keep their export files inside tools.jar
    public Optional<Path> exportDir() {
        return Files.isDirectory(exportDir) ? Optional.of(exportDir) : Optional.empty();
    }

    @Override
    public String toString() {
        return version.toString();
    }
}
