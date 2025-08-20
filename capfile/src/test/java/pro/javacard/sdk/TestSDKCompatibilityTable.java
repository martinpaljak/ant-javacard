package pro.javacard.sdk;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSDKCompatibilityTable {

    @Test
    public void testJavaClassFileVersionMapping() {
        // Test that getJavaClassFileVersion returns expected values
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V211), "1.1");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V221), "1.2");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V222), "1.5");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V304), "1.6");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V305), "1.6");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V310), "1.7");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V320), "1.7");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V320_24_1), "1.7");
        Assert.assertEquals(SDKCompatibilityTable.getJavaClassFileVersion(SDKVersion.V320_25_0), "1.8");
    }

    @Test
    public void testJDKCompatibility() {
        // Test JDK compatibility for 2.x versions
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V222, 8));
        Assert.assertFalse(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V222, 11));
        
        // Test JDK compatibility for 3.0.x versions
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V304, 8));
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V304, 11));
        Assert.assertFalse(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V304, 17));
        
        // Test JDK compatibility for 3.1.0
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V310, 11));
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V310, 17));
        Assert.assertFalse(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V310, 21));
        
        // Test JDK compatibility for 3.2.0 variants
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V320_24_1, 11));
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V320_24_1, 17));
        Assert.assertFalse(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V320_24_1, 8));
        
        // Test V320_25_0 supports newer JDK versions
        Assert.assertTrue(SDKCompatibilityTable.isJDKCompatible(SDKVersion.V320_25_0, 21));
    }

    @Test
    public void testMultitargeting() {
        // Test that only V310 and V32x versions support multitargeting
        Assert.assertFalse(SDKCompatibilityTable.isMultitarget(SDKVersion.V222));
        Assert.assertFalse(SDKCompatibilityTable.isMultitarget(SDKVersion.V304));
        Assert.assertTrue(SDKCompatibilityTable.isMultitarget(SDKVersion.V310));
        Assert.assertTrue(SDKCompatibilityTable.isMultitarget(SDKVersion.V320));
        Assert.assertTrue(SDKCompatibilityTable.isMultitarget(SDKVersion.V320_24_1));
        Assert.assertTrue(SDKCompatibilityTable.isMultitarget(SDKVersion.V320_25_0));
    }

    @Test 
    public void testTargeting() {
        // Test that V310 can target appropriate versions
        Assert.assertTrue(SDKCompatibilityTable.canTarget(SDKVersion.V310, SDKVersion.V304));
        Assert.assertTrue(SDKCompatibilityTable.canTarget(SDKVersion.V310, SDKVersion.V305));
        Assert.assertTrue(SDKCompatibilityTable.canTarget(SDKVersion.V310, SDKVersion.V310));
        Assert.assertFalse(SDKCompatibilityTable.canTarget(SDKVersion.V310, SDKVersion.V320));
        
        // Test that V320 variants can target appropriate versions
        Assert.assertTrue(SDKCompatibilityTable.canTarget(SDKVersion.V320, SDKVersion.V304));
        Assert.assertTrue(SDKCompatibilityTable.canTarget(SDKVersion.V320_24_1, SDKVersion.V305));
        
        // Test that older versions cannot target newer versions
        Assert.assertFalse(SDKCompatibilityTable.canTarget(SDKVersion.V304, SDKVersion.V310));
        Assert.assertFalse(SDKCompatibilityTable.canTarget(SDKVersion.V222, SDKVersion.V304));
    }

    @Test
    public void testMinMaxJDKVersions() {
        // Test min/max JDK version getters
        Assert.assertEquals(SDKCompatibilityTable.getMinJDKVersion(SDKVersion.V320_24_1), 11);
        Assert.assertEquals(SDKCompatibilityTable.getMaxJDKVersion(SDKVersion.V304), 11);
        Assert.assertEquals(SDKCompatibilityTable.getMaxJDKVersion(SDKVersion.V320_25_0), Integer.MAX_VALUE);
    }

    @Test
    public void testGetTargetableVersions() {
        // Test getting all targetable versions
        var v310Targets = SDKCompatibilityTable.getTargetableVersions(SDKVersion.V310);
        Assert.assertTrue(v310Targets.contains(SDKVersion.V304));
        Assert.assertTrue(v310Targets.contains(SDKVersion.V305));
        Assert.assertTrue(v310Targets.contains(SDKVersion.V310));
        Assert.assertFalse(v310Targets.contains(SDKVersion.V320));
        
        var v222Targets = SDKCompatibilityTable.getTargetableVersions(SDKVersion.V222);
        Assert.assertTrue(v222Targets.isEmpty());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownSDKVersion() {
        // This would fail if we added a new SDK version but forgot to update the table
        // For now, just test that the method throws for null (simulating unknown version)
        SDKCompatibilityTable.getCompatibility(null);
    }
}