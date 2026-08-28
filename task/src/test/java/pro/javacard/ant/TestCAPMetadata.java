// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.testng.annotations.Test;
import pro.javacard.capfile.AID;
import pro.javacard.capfile.CAPFile;
import pro.javacard.capfile.CAPMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.jar.JarInputStream;

import static org.testng.Assert.*;

// applet.cap and library.cap are converted into target/caps by the build, with jc320v26.0_kit
public class TestCAPMetadata {

    private static final Path CAPS = Paths.get("target", "caps");
    private static final AID PACKAGE = new AID("010203040506");
    private static final AID APPLET = new AID("01020304050601");
    private static final String APPLET_CLASS = "testapplets.empty.Empty";
    private static final String APPLET_XML = "APPLET-INF/applet.xml";

    // What the converter wrote, read back through the model
    @Test
    public void readsTheConverterFormat() throws IOException {
        CAPMetadata meta = cap("applet.cap").getMetadata();
        assertModel(meta);
        assertEquals(meta.getCapFileVersion(), Optional.of("2.3"));
        assertEquals(meta.getImports().size(), 3);
        // Without a source date the converter's own JDK and moment are still there
        assertTrue(meta.getField("Created-By").isPresent());
        assertTrue(meta.getField("Java-Card-CAP-Creation-Time").isPresent());
        assertTrue(meta.getField("Java-Card-Converter-Version").isPresent());

        // A library says the same about itself, without applets
        CAPMetadata library = cap("library.cap").getMetadata();
        assertEquals(library.getName(), "testapplets.library");
        assertTrue(library.getApplets().isEmpty());

        // A source date replaces the creator and the creation time, and carries the rest over
        CAPMetadata stamped = CAPMetadata.from(new ByteArrayInputStream(
                meta.toManifest(LocalDateTime.of(2009, 2, 13, 23, 31, 30))), null);
        assertEquals(stamped.getField("created-by"), Optional.of("pro.javacard.capfile"));
        assertEquals(stamped.getField("java-card-cap-creation-time"), Optional.of("Fri Feb 13 23:31:30 UTC 2009"));
        assertEquals(stamped.getField("Java-Card-Converter-Version"), meta.getField("Java-Card-Converter-Version"));
        assertPackage(stamped);
    }

    // Both metadata files are written, whether or not the converting kit wrote them
    @Test
    public void writesBothMetadataFiles() throws IOException {
        Path had = leanCopy("applet.cap");
        Path hadNot = leanCopy("applet.cap", APPLET_XML);
        CAPFile.amendMetadata(had, Collections.singletonMap(APPLET, APPLET_CLASS));
        CAPFile.amendMetadata(hadNot, Collections.singletonMap(APPLET, APPLET_CLASS));
        assertEquals(component(hadNot, APPLET_XML), component(had, APPLET_XML));
        assertEquals(component(hadNot, "META-INF/MANIFEST.MF"), component(had, "META-INF/MANIFEST.MF"));
        assertModel(CAPFile.fromFile(hadNot).getMetadata());

        // Without the class from the build the CAP file keeps the applet AID alone
        Path unnamed = leanCopy("applet.cap", APPLET_XML);
        CAPFile.amendMetadata(unnamed);
        CAPFile bare = CAPFile.fromFile(unnamed);
        assertFalse(bare.getZipComponent(APPLET_XML).isPresent());
        assertFalse(bare.getMetadata().getApplets().get(0).getClassName().isPresent());
        assertEquals(bare.getMetadata().getApplets().get(0).getAid(), APPLET);

        // A library has no applets, so it gets no applet.xml.
        Path library = leanCopy("library.cap");
        CAPFile.amendMetadata(library);
        assertFalse(CAPFile.fromFile(library).getZipComponent(APPLET_XML).isPresent());
    }

    @Test
    public void writesBothFilesWhereTheKitWroteNeither() throws IOException {
        Path bare = leanCopy("applet.cap", APPLET_XML, "META-INF/javacard.xml", "META-INF/MANIFEST.MF");
        assertFalse(CAPFile.fromFile(bare).getMetadata().getField("Java-Card-Converter-Version").isPresent());
        CAPFile.amendMetadata(bare, Collections.singletonMap(APPLET, APPLET_CLASS));
        assertModel(CAPFile.fromFile(bare).getMetadata());
        assertEquals(component(bare, APPLET_XML), component(CAPS.resolve("applet.cap"), APPLET_XML));

        // The manifest leads the archive, where streaming JAR consumers expect it.
        try (JarInputStream jar = new JarInputStream(Files.newInputStream(bare))) {
            assertNotNull(jar.getManifest(), "manifest must be visible to streaming JAR consumers");
            assertEquals(CAPMetadata.from(jar.getManifest(), null).getAid(), PACKAGE);
        }
    }

    @Test
    public void amendingIsReproducible() throws IOException {
        Path amended = leanCopy("applet.cap");
        CAPFile.amendMetadata(amended, Collections.singletonMap(APPLET, APPLET_CLASS));
        byte[] once = Files.readAllBytes(amended);
        // Amending an already amended CAP changes nothing, and so does a copy amended on its own
        CAPFile.amendMetadata(amended, Collections.singletonMap(APPLET, APPLET_CLASS));
        assertEquals(Files.readAllBytes(amended), once);
        Path other = leanCopy("applet.cap");
        CAPFile.amendMetadata(other, Collections.singletonMap(APPLET, APPLET_CLASS));
        assertEquals(Files.readAllBytes(other), once);
    }

    // What a CAP file says about the package, whichever kit converted it.
    private static void assertModel(CAPMetadata meta) {
        assertPackage(meta);
        assertEquals(meta.getApplets().get(0).getClassName(), Optional.of(APPLET_CLASS));
    }

    // The same, without the class, which only applet.xml states.
    private static void assertPackage(CAPMetadata meta) {
        assertEquals(meta.getAid(), PACKAGE);
        assertEquals(meta.getName(), "testapplets.empty");
        assertEquals(meta.getVersion(), "1.0");
        assertEquals(meta.getField("Java-Card-Integer-Support-Required"), Optional.of("FALSE"));
        assertEquals(meta.getField("Application-Type"), Optional.of("classic-applet"));
        assertEquals(meta.getApplets().size(), 1);
        assertEquals(meta.getApplets().get(0).getAid(), APPLET);
        assertEquals(meta.getApplets().get(0).getName(), Optional.of("Empty"));
        // javacard.framework, imported by every applet; its version follows the kit.
        assertTrue(meta.getImports().stream().anyMatch(i -> i.getAid().equals(new AID("A0000000620101"))));
    }

    private static CAPFile cap(String name) throws IOException {
        return CAPFile.fromFile(CAPS.resolve(name));
    }

    // A CAP copy with the given entries removed, as a leaner or older converter would emit.
    private static Path leanCopy(String name, String... removedEntries) throws IOException {
        Path tmp = Files.createTempFile("cap", ".cap");
        tmp.toFile().deleteOnExit();
        Files.copy(CAPS.resolve(name), tmp, StandardCopyOption.REPLACE_EXISTING);
        if (removedEntries.length > 0) {
            try (FileSystem zip = FileSystems.newFileSystem(tmp, (ClassLoader) null)) {
                for (String entry : removedEntries) {
                    Files.deleteIfExists(zip.getPath(entry));
                }
            }
        }
        return tmp;
    }

    private static byte[] component(Path cap, String name) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(cap, (ClassLoader) null)) {
            return Files.readAllBytes(zip.getPath(name));
        }
    }
}
