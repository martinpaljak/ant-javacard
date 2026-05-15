// SPDX-FileCopyrightText: 2024 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class TestSDKs {

    static Path sdksRoot() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("sdks");
    }

    static JavaCardSDK sdk(String name) {
        return JavaCardSDK.detectSDK(sdksRoot().resolve(name)).orElseThrow(() -> new AssertionError("SDK not found: " + name));
    }

    @Test
    public void testDetection() throws Exception {
        try (Stream<Path> dirs = Files.list(sdksRoot())) {
            dirs.forEach(dir -> {
                if (Files.isDirectory(dir)) {
                    System.out.println(String.format("%s: %s", dir, JavaCardSDK.detectSDK(dir).map(JavaCardSDK::getRelease).orElse("not SDK")));
                    Assert.assertTrue(JavaCardSDK.detectSDK(dir).isPresent(), "Failed to detect SDK in " + dir);
                }
            });
        }

        // Pinned version and release assertions
        Assert.assertEquals(sdk("jc211_kit").getVersion(), SDKVersion.V211);
        Assert.assertEquals(sdk("jc211_kit").getRelease(), "2.1.1");
        Assert.assertEquals(sdk("jc212_kit").getVersion(), SDKVersion.V212);
        Assert.assertEquals(sdk("jc221_kit").getVersion(), SDKVersion.V221);
        Assert.assertEquals(sdk("jc222_kit").getVersion(), SDKVersion.V222);
        Assert.assertEquals(sdk("jc303_kit").getVersion(), SDKVersion.V301);
        Assert.assertEquals(sdk("jc304_kit").getVersion(), SDKVersion.V304);
        Assert.assertEquals(sdk("jc305u1_kit").getRelease(), "3.0.5u1");
        Assert.assertEquals(sdk("jc305u2_kit").getRelease(), "3.0.5u2");
        Assert.assertEquals(sdk("jc305u3_kit").getRelease(), "3.0.5u3");
        Assert.assertEquals(sdk("jc305u4_kit").getRelease(), "3.0.5u3"); // u4 indistinguishable from u3
        Assert.assertEquals(sdk("jc310b43_kit").getVersion(), SDKVersion.V310);
        Assert.assertEquals(sdk("jc320v25.1_kit").getVersion(), SDKVersion.V320_25_1);
        Assert.assertEquals(sdk("jc320v26.0_kit").getVersion(), SDKVersion.V320_26_0);

        // 3.2.0 family release labels (year.release notation)
        Assert.assertEquals(sdk("jc320v24.0_kit").getRelease(), "24.0");
        Assert.assertEquals(sdk("jc320v24.1_kit").getRelease(), "24.1");
        Assert.assertEquals(sdk("jc320v25.0_kit").getRelease(), "25.0");
        Assert.assertEquals(sdk("jc320v25.1_kit").getRelease(), "25.1");
        Assert.assertEquals(sdk("jc320v26.0_kit").getRelease(), "26.0");

        // Java class file target versions
        Assert.assertEquals(SDKVersion.V211.javaVersion(), "1.1");
        Assert.assertEquals(SDKVersion.V222.javaVersion(), "1.5");
        Assert.assertEquals(SDKVersion.V301.javaVersion(), "1.6");
        Assert.assertEquals(SDKVersion.V305.javaVersion(), "1.7");
        Assert.assertEquals(SDKVersion.V320_25_1.javaVersion(), "1.8");
        Assert.assertEquals(SDKVersion.V320_26_0.javaVersion(), "1.8");

        // Multi-target SDKs
        Assert.assertTrue(SDKVersion.V310.targets().contains(SDKVersion.V304));
        Assert.assertTrue(SDKVersion.V310.targets().contains(SDKVersion.V305));
        Assert.assertTrue(SDKVersion.V320.targets().contains(SDKVersion.V310));

        // v26.0 preview targets (api_export_files_preview / api_export_files_preview-final in tools.jar)
        Assert.assertTrue(SDKVersion.V320_26_0.targets().contains(SDKVersion.V320_PREVIEW));
        Assert.assertTrue(SDKVersion.V320_26_0.targets().contains(SDKVersion.V320_PREVIEW_FINAL));
        Assert.assertEquals(SDKVersion.V320_PREVIEW.toString(), "preview");
        Assert.assertEquals(SDKVersion.V320_PREVIEW_FINAL.toString(), "preview-final");
        Assert.assertEquals(sdk("jc320v26.0_kit").target(SDKVersion.V320_PREVIEW).getRelease(), "preview");
        Assert.assertEquals(sdk("jc320v26.0_kit").target(SDKVersion.V320_PREVIEW_FINAL).getRelease(), "preview-final");
        // Older v25.x must NOT carry preview targets
        Assert.assertFalse(SDKVersion.V320_25_1.targets().contains(SDKVersion.V320_PREVIEW));

        // JDK support
        Assert.assertTrue(SDKVersion.V305.jdkVersions().contains(17));
        Assert.assertTrue(SDKVersion.V320_25_1.jdkVersions().contains(21));
        Assert.assertTrue(SDKVersion.V320_25_1.jdkVersions().contains(25));
        Assert.assertTrue(SDKVersion.V320_26_0.jdkVersions().contains(25));

        // Version ordering and lookup
        Assert.assertTrue(SDKVersion.V320.equalOrNewer(SDKVersion.V211));
        Assert.assertFalse(SDKVersion.V211.equalOrNewer(SDKVersion.V222));
        Assert.assertEquals(SDKVersion.fromVersion("3.0.5"), Optional.of(SDKVersion.V305));
        Assert.assertFalse(SDKVersion.fromVersion("9.9.9").isPresent());
    }
}
