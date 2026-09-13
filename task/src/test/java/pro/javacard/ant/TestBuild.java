// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.Project;
import org.testng.annotations.Test;
import pro.javacard.capfile.AID;
import pro.javacard.sdk.APIVersion;
import pro.javacard.sdk.SDKRelease;

import java.nio.file.Paths;

import static org.testng.Assert.*;

// Checks resolution without running a build
public class TestBuild {

    // A <cap> with only the project and the SDK set
    private static JCCap withSDK() {
        Project project = new Project();
        project.setBaseDir(Paths.get(System.getProperty("user.dir")).getParent().toFile());
        JCCap cap = new JCCap(null);
        cap.setProject(project);
        cap.setJCKit("sdks/jc320v26.0_kit");
        return cap;
    }

    private static JCCap declared() {
        JCCap cap = withSDK();
        cap.setSources("src/testapplets/empty");
        return cap;
    }

    @Test
    public void derivesTheRestFromTheApplet() {
        JCCap cap = declared();
        cap.setAID("0102030405");
        cap.createApplet().setClass("testapplets.empty.Empty");

        Build build = cap.resolve();

        assertEquals(build.packageName(), "testapplets.empty");
        assertEquals(build.name(), "empty");
        // The package AID and the number of the applet make the applet AID
        assertEquals(build.applets.keySet().iterator().next(), new AID("010203040501"));
        assertEquals(build.firstApplet(), "testapplets.empty.Empty");
        assertEquals(build.pkg.getVersionString(), "0.0");
        assertEquals(build.target.version(), APIVersion.V320);
        assertTrue(build.verify);
        assertTrue(build.compiles());
        assertNull(build.classes);
        assertTrue(build.cap.endsWith(JCCap.DEFAULT_CAP_NAME_TEMPLATE), build.cap);
    }

    @Test
    public void aPackageWithoutAppletsIsALibrary() {
        JCCap cap = declared();
        cap.setAID("0102030405");
        cap.setPackage("testapplets.empty");
        cap.setVersion("1.2");
        cap.setTargetsdk("3.0.4");

        Build build = cap.resolve();

        assertTrue(build.applets.isEmpty());
        assertNull(build.firstApplet());
        assertEquals(build.pkg.getVersionString(), "1.2");
        assertEquals(build.target.version(), APIVersion.V304);
        assertTrue(build.cap.endsWith(JCCap.DEFAULT_CAP_NAME_TEMPLATE_LIB), build.cap);

        cap.setOutput("nosuchdir/");
        String into = cap.resolve().cap;
        assertTrue(into.endsWith(Paths.get("nosuchdir", JCCap.DEFAULT_CAP_NAME_TEMPLATE_LIB).toString()), into);
    }

    @Test
    public void refusesWhatItCanNotDerive() {
        // A top-level filter next to nested <sources>
        JCCap filtered = withSDK();
        filtered.setAID("0102030405");
        filtered.setPackage("testapplets.empty");
        filtered.setIncludes("**/*.java");
        filtered.createSources().setPath("src/testapplets/empty");
        assertThrows(HelpingBuildException.class, filtered::resolve);

        // An <import> naming neither a folder nor a JAR
        JCCap nothing = declared();
        nothing.setAID("0102030405");
        nothing.setPackage("testapplets.empty");
        nothing.createImport();
        assertThrows(HelpingBuildException.class, nothing::resolve);

        // An applet number does not fit into a 16 byte package AID
        JCCap full = declared();
        full.setAID("0102030405060708090A0B0C0D0E0F10");
        full.createApplet().setClass("testapplets.empty.Empty");
        assertThrows(HelpingBuildException.class, full::resolve);
    }

    @Test(expectedExceptions = HelpingBuildException.class)
    public void refusesAnAppletOutsideThePackage() {
        JCCap cap = declared();
        cap.setAID("0102030405");
        cap.setPackage("testapplets.empty");
        cap.createApplet().setClass("testapplets.other.Empty");
        cap.resolve();
    }

    @Test
    public void compilesForTheConverterAndTheJDK() {
        // v26.0 eats up to class file 54 (Java 10)
        assertEquals(SDKRelease.V26_0.javacTarget(8), "1.8");
        assertEquals(SDKRelease.V26_0.javacTarget(11), "10");
        assertEquals(SDKRelease.V26_0.javacTarget(25), "10");
        // Older converters cap below every JDK that runs them
        assertEquals(SDKRelease.V24_1.javacTarget(17), "1.7");
        assertEquals(SDKRelease.V222.javacTarget(8), "1.5");
        assertEquals(SDKRelease.V211.javacTarget(8), "1.1");
        // v24.1 is the one SDK that JDK 8 can not load
        assertEquals(SDKRelease.V24_1.oldestJDK(), 11);
        assertEquals(SDKRelease.V305u4.newestJDK(), 19);
    }

    @Test(expectedExceptions = HelpingBuildException.class)
    public void refusesAnAPIVersionTheSDKDoesNotHave() {
        JCCap cap = declared();
        cap.setAID("0102030405");
        cap.setPackage("testapplets.empty");
        cap.setTargetsdk("2.2.2");
        cap.resolve();
    }
}
