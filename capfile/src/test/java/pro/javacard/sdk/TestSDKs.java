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

        // Java class file target versions
        Assert.assertEquals(SDKVersion.V211.javaVersion(), "1.1");
        Assert.assertEquals(SDKVersion.V222.javaVersion(), "1.5");
        Assert.assertEquals(SDKVersion.V301.javaVersion(), "1.6");
        Assert.assertEquals(SDKVersion.V320_25_1.javaVersion(), "1.8");

        // Multi-target SDKs
        Assert.assertTrue(SDKVersion.V310.targets().contains(SDKVersion.V304));
        Assert.assertTrue(SDKVersion.V310.targets().contains(SDKVersion.V305));
        Assert.assertTrue(SDKVersion.V320.targets().contains(SDKVersion.V310));

        // JDK support
        Assert.assertTrue(SDKVersion.V320_25_1.jdkVersions().contains(21));

        // Version ordering and lookup
        Assert.assertTrue(SDKVersion.V320.equalOrNewer(SDKVersion.V211));
        Assert.assertFalse(SDKVersion.V211.equalOrNewer(SDKVersion.V222));
        Assert.assertEquals(SDKVersion.fromVersion("3.0.5"), Optional.of(SDKVersion.V305));
        Assert.assertFalse(SDKVersion.fromVersion("9.9.9").isPresent());
    }
}
