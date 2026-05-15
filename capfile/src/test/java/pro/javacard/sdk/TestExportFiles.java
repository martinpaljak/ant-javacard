// SPDX-FileCopyrightText: 2024 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import org.testng.Assert;
import org.testng.annotations.Test;
import pro.javacard.capfile.HexUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TestExportFiles {

    static Path sdksRoot() {
        return Paths.get(System.getProperty("user.dir")).getParent().resolve("sdks");
    }

    @Test
    public void testParseAllExportFiles() throws Exception {
        int total = 0;
        int failures = 0;

        try (Stream<Path> dirs = Files.list(sdksRoot())) {
            List<Path> sdkDirs = dirs.filter(Files::isDirectory).sorted().collect(Collectors.toList());
            for (Path dir : sdkDirs) {
                Optional<JavaCardSDK> sdk = JavaCardSDK.detectSDK(dir);
                Assert.assertTrue(sdk.isPresent(), "Failed to detect SDK in " + dir);

                List<Path> exportDirs = JavaCardSDK.getAllExportDirs(sdk.get().getVersion());
                ArrayList<String> jarPrefixes = new ArrayList<String>();

                for (Path exportDir : exportDirs) {
                    Path fsDir = dir.resolve(exportDir);
                    if (Files.isDirectory(fsDir)) {
                        try (Stream<Path> walk = Files.walk(fsDir)) {
                            List<Path> expFiles = walk.filter(p -> p.toString().endsWith(".exp"))
                                    .filter(Files::isRegularFile)
                                    .sorted()
                                    .collect(Collectors.toList());
                            for (Path exp : expFiles) {
                                total++;
                                try {
                                    ExportFileHelper.PackageInfo pkg = ExportFileHelper.parsePackage(exp);
                                    System.out.println(exp);
                                    System.out.println("  " + pkg);
                                } catch (Exception e) {
                                    System.err.println(String.format("%s: FAILED - %s", exp, e.getMessage()));
                                    failures++;
                                }
                            }
                        }
                    } else {
                        jarPrefixes.add(exportDir.toString() + "/");
                    }
                }

                if (!jarPrefixes.isEmpty()) {
                    Path toolsJar = dir.resolve("lib").resolve("tools.jar");
                    if (Files.exists(toolsJar)) {
                        try (ZipFile zf = new ZipFile(toolsJar.toFile())) {
                            Enumeration<? extends ZipEntry> entries = zf.entries();
                            while (entries.hasMoreElements()) {
                                ZipEntry entry = entries.nextElement();
                                if (!entry.getName().endsWith(".exp")) {
                                    continue;
                                }
                                if (jarPrefixes.stream().noneMatch(entry.getName()::startsWith)) {
                                    continue;
                                }
                                total++;
                                try {
                                    ExportFileHelper.PackageInfo pkg = ExportFileHelper.parsePackage(zf.getInputStream(entry));
                                    System.out.println(String.format("%s!%s", toolsJar, entry.getName()));
                                    System.out.println("  " + pkg);
                                } catch (Exception e) {
                                    System.err.println(String.format("%s!%s: FAILED - %s", toolsJar, entry.getName(), e.getMessage()));
                                    failures++;
                                }
                            }
                        }
                    }
                }
            }
        }

        Assert.assertTrue(total > 100, String.format("Expected at least 100 .exp files, found %d", total));
        Assert.assertEquals(failures, 0, String.format("%d export files failed to parse", failures));

        // Pinned assertions for specific files
        ExportFileHelper.PackageInfo v21 = ExportFileHelper.parsePackage(
                sdksRoot().resolve("jc211_kit/api_export_files/javacard/framework/javacard/framework.exp"));
        Assert.assertEquals(v21.getVersion(), ExportFileHelper.ExportFileVersion.V21);
        Assert.assertEquals(v21.getName(), "javacard.framework");
        Assert.assertEquals(v21.getAid(), HexUtils.hex2bin("A0000000620101"));
        Assert.assertEquals(v21.getMajor(), 1);
        Assert.assertEquals(v21.getMinor(), 0);
        Assert.assertEquals(v21.getPackageVersion(), "1.0");
        Assert.assertTrue(v21.isLibrary());

        ExportFileHelper.PackageInfo v22 = ExportFileHelper.parsePackage(
                sdksRoot().resolve("jc221_kit/api_export_files/javacard/framework/service/javacard/service.exp"));
        Assert.assertEquals(v22.getVersion(), ExportFileHelper.ExportFileVersion.V22);
        Assert.assertEquals(v22.getName(), "javacard.framework.service");
        Assert.assertEquals(v22.getAid(), HexUtils.hex2bin("A000000062010101"));

        ExportFileHelper.PackageInfo v23 = ExportFileHelper.parsePackage(
                sdksRoot().resolve("jc310b43_kit/api_export_files_3.0.4/java/lang/javacard/lang.exp"));
        Assert.assertEquals(v23.getVersion(), ExportFileHelper.ExportFileVersion.V23);
        Assert.assertEquals(v23.getName(), "java.lang");
        Assert.assertEquals(v23.getAid(), HexUtils.hex2bin("A0000000620001"));

        // Same package across export file versions - same identity, different format
        Assert.assertEquals(v21.getName(), "javacard.framework");
        Assert.assertEquals(v23.getName(), "java.lang");

        // V23 export files with multiple CONSTANT_Package entries (imports + this_package)
        ExportFileHelper.PackageInfo v23fw = ExportFileHelper.parsePackage(
                sdksRoot().resolve("jc310b43_kit/api_export_files_3.1.0/javacard/framework/javacard/framework.exp"));
        Assert.assertEquals(v23fw.getVersion(), ExportFileHelper.ExportFileVersion.V23);
        Assert.assertEquals(v23fw.getName(), "javacard.framework");
        Assert.assertEquals(v23fw.getAid(), HexUtils.hex2bin("A0000000620101"));
        Assert.assertEquals(v23fw.getMajor(), 1);
        Assert.assertEquals(v23fw.getMinor(), 8);

        ExportFileHelper.PackageInfo v23sec = ExportFileHelper.parsePackage(
                sdksRoot().resolve("jc310b43_kit/api_export_files_3.1.0/javacard/security/javacard/security.exp"));
        Assert.assertEquals(v23sec.getVersion(), ExportFileHelper.ExportFileVersion.V23);
        Assert.assertEquals(v23sec.getName(), "javacard.security");
        Assert.assertEquals(v23sec.getAid(), HexUtils.hex2bin("A0000000620102"));
        Assert.assertEquals(v23sec.getMajor(), 1);
        Assert.assertEquals(v23sec.getMinor(), 7);

        // Non-library package
        ExportFileHelper.PackageInfo nonLib = ExportFileHelper.parsePackage(
                sdksRoot().resolve("jc304_kit/classic_simulator/api_export_files/com/sun/javacard/installer/javacard/installer.exp"));
        Assert.assertEquals(nonLib.getName(), "com.sun.javacard.installer");
        Assert.assertEquals(nonLib.getAid(), HexUtils.hex2bin("A000000062030108"));
        Assert.assertFalse(nonLib.isLibrary());

        // v26.0 "preview" target: api_export_files_preview/ inside tools.jar
        Path tools26 = sdksRoot().resolve("jc320v26.0_kit/lib/tools.jar");
        try (ZipFile zf = new ZipFile(tools26.toFile())) {
            // javacard.security bumped 1.8 (3.2.0) -> 1.9 (preview)
            ExportFileHelper.PackageInfo sec = ExportFileHelper.parsePackage(
                    zf.getInputStream(zf.getEntry("api_export_files_preview/javacard/security/javacard/security.exp")));
            Assert.assertEquals(sec.getVersion(), ExportFileHelper.ExportFileVersion.V23);
            Assert.assertEquals(sec.getName(), "javacard.security");
            Assert.assertEquals(sec.getAid(), HexUtils.hex2bin("A0000000620102"));
            Assert.assertEquals(sec.getPackageVersion(), "1.9");

            // New preview-only package javacardx.security.bdh
            ExportFileHelper.PackageInfo bdh = ExportFileHelper.parsePackage(
                    zf.getInputStream(zf.getEntry("api_export_files_preview/javacardx/security/bdh/javacard/bdh.exp")));
            Assert.assertEquals(bdh.getName(), "javacardx.security.bdh");
            Assert.assertEquals(bdh.getAid(), HexUtils.hex2bin("A000000062020504"));
            Assert.assertEquals(bdh.getPackageVersion(), "1.0");

            // preview-final mirrors preview in this kit
            ExportFileHelper.PackageInfo secFinal = ExportFileHelper.parsePackage(
                    zf.getInputStream(zf.getEntry("api_export_files_preview-final/javacard/security/javacard/security.exp")));
            Assert.assertEquals(secFinal.getPackageVersion(), "1.9");
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMagic() throws Exception {
        ExportFileHelper.parsePackage(new ByteArrayInputStream(HexUtils.hex2bin("000000000102")));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMajorVersion() throws Exception {
        ExportFileHelper.parsePackage(new ByteArrayInputStream(HexUtils.hex2bin("00FACADE0103")));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMinorVersion() throws Exception {
        ExportFileHelper.parsePackage(new ByteArrayInputStream(HexUtils.hex2bin("00FACADE0402")));
    }
}
