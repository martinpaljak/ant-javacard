// SPDX-FileCopyrightText: 2024 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TestSDKs {

    static Path sdksRoot() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("sdks");
    }

    static List<Path> sdkFolders() throws IOException {
        try (Stream<Path> dirs = Files.list(sdksRoot())) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    static void toolsJar(Path jar, String properties) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("com/sun/javacard/toolsversion.properties"));
            zip.write(properties.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    static JavaCardSDK sdk(String name) {
        return JavaCardSDK.detectSDK(sdksRoot().resolve(name)).orElseThrow(() -> new AssertionError("SDK not found: " + name));
    }

    // What detection must report for every folder in sdks/
    private static final Map<String, SDKRelease> EXPECTED = Map.ofEntries(
            Map.entry("jc211_kit", SDKRelease.V211),
            Map.entry("jc212_kit", SDKRelease.V212),
            Map.entry("jc221_kit", SDKRelease.V221),
            Map.entry("jc222_kit", SDKRelease.V222),
            Map.entry("jc303_kit", SDKRelease.V301),
            Map.entry("jc304_kit", SDKRelease.V304),
            Map.entry("jc305u1_kit", SDKRelease.V305u1),
            Map.entry("jc305u2_kit", SDKRelease.V305u2),
            Map.entry("jc305u3_kit", SDKRelease.V305u3),
            Map.entry("jc305u4_kit", SDKRelease.V305u4),
            Map.entry("jc310b43_kit", SDKRelease.V310),
            Map.entry("jc310r20210706_kit", SDKRelease.V310),
            Map.entry("jc320v24.0_kit", SDKRelease.V24_0),
            Map.entry("jc320v24.1_kit", SDKRelease.V24_1),
            Map.entry("jc320v25.0_kit", SDKRelease.V25_0),
            Map.entry("jc320v25.1_kit", SDKRelease.V25_1),
            Map.entry("jc320v26.0_kit", SDKRelease.V26_0));

    @Test
    public void testDetection() throws Exception {
        for (Path dir : sdkFolders()) {
            String name = dir.getFileName().toString();
            JavaCardSDK sdk = JavaCardSDK.detectSDK(dir).orElseThrow(() -> new AssertionError("Failed to detect SDK in " + dir));
            Assert.assertEquals(sdk.getRelease(), EXPECTED.get(name), name);
        }
        Assert.assertEquals(sdkFolders().size(), EXPECTED.size(), "sdks/ and the table above have diverged");

        Assert.assertEquals(sdk("jc320v26.0_kit").api(APIVersion.PREVIEW).get().version(), APIVersion.PREVIEW);
        Assert.assertEquals(sdk("jc320v26.0_kit").api(APIVersion.PREVIEW).get().sdk().getRelease(), SDKRelease.V26_0);

        Assert.assertTrue(APIVersion.V320.equalOrNewer(APIVersion.V211));
        Assert.assertFalse(APIVersion.V211.equalOrNewer(APIVersion.V222));
        Assert.assertEquals(APIVersion.fromVersion("3.0.5"), Optional.of(APIVersion.V305));
        Assert.assertFalse(APIVersion.fromVersion("9.9.9").isPresent());
    }

    @Test
    public void testUnsupportedSDK() throws Exception {
        Path root = Files.createTempDirectory("sdk");
        Path tools = Files.createDirectories(root.resolve("lib")).resolve("tools.jar");
        try {
            // Nothing identifying the release: not an SDK
            toolsJar(tools, "copyright.banner=Copyright (c) 2020, Oracle\n");
            Assert.assertFalse(JavaCardSDK.detectSDK(root).isPresent());

            toolsJar(tools, "converter.version=99.9\n");
            try {
                JavaCardSDK.detectSDK(root);
                Assert.fail("An unknown converter version must not pass as a supported SDK");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("99.9"), e.getMessage());
                Assert.assertTrue(e.getMessage().contains(root.toString()), e.getMessage());
            }
        } finally {
            OffCardVerifier.rmminusrf(root);
        }
    }
}
