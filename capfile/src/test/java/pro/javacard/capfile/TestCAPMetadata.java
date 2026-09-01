// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.capfile;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.testng.Assert.*;

// Metadata for a simulated package, with no CAP file to read it off.
// Metadata from real converter output is tested in the task module.
public class TestCAPMetadata {

    private static final AID PACKAGE = new AID("0102030405");
    private static final AID APPLET = new AID("0102030405060708");
    private static final String APPLET_CLASS = "com.example.applet.MyApplet";

    private static CAPMetadata applet() {
        return new CAPMetadata(PACKAGE, "com.example.applet", "0.1",
                Collections.singletonList(new CAPMetadata.Applet(APPLET, APPLET_CLASS)));
    }

    private static String crlf(String... lines) {
        return String.join("\r\n", lines);
    }

    private static CAPMetadata parse(String manifest) throws Exception {
        return parse(manifest.getBytes(StandardCharsets.UTF_8));
    }

    private static CAPMetadata parse(byte[] manifest) throws Exception {
        return CAPMetadata.from(new ByteArrayInputStream(manifest), null);
    }

    @Test
    public void testRoundTrips() throws Exception {
        CAPMetadata meta = applet();
        CAPMetadata read = parse(meta.toManifest());
        assertEquals(read.getAid(), PACKAGE);
        assertEquals(read.getName(), "com.example.applet");
        assertEquals(read.getVersion(), "0.1");
        assertEquals(read.getField("application-type"), Optional.of("classic-applet"));
        assertEquals(read.getApplets().size(), 1);
        assertEquals(read.getApplets().get(0).getAid(), APPLET);
        assertEquals(read.getApplets().get(0).getName(), Optional.of("MyApplet"));
        assertEquals(read.toManifest(), meta.toManifest());

        // Nothing carried and no time given: the manifest says nothing about its own making
        assertFalse(read.getField(CAPMetadata.CREATED_BY).isPresent());
        assertFalse(read.getField(CAPMetadata.CREATION_TIME).isPresent());
        assertFalse(read.getField(CAPMetadata.CONVERTER_VERSION).isPresent());
        assertFalse(read.getCapFileVersion().isPresent());

        // A time stamps the creation time and names this library as the creator
        CAPMetadata stamped = parse(meta.toManifest(LocalDateTime.of(2009, 2, 13, 23, 31, 30)));
        assertEquals(stamped.getField(CAPMetadata.CREATED_BY), Optional.of("pro.javacard.capfile"));
        assertEquals(stamped.getField("JAVA-CARD-CAP-CREATION-TIME"), Optional.of("Fri Feb 13 23:31:30 UTC 2009"));

        // The class comes from applet.xml, which the manifest does not carry
        assertFalse(read.getApplets().get(0).getClassName().isPresent());
        CAPMetadata both = CAPMetadata.from(new ByteArrayInputStream(meta.toManifest()),
                new ByteArrayInputStream(meta.toAppletXml()));
        assertEquals(both.getApplets().get(0).getClassName(), Optional.of(APPLET_CLASS));
        assertEquals(CAPMetadata.appletClasses(new ByteArrayInputStream(meta.toAppletXml())),
                Collections.singletonMap(APPLET, APPLET_CLASS));

        // A name past the manifest line limit folds and comes back whole
        String name = "com.example.a.very.long.package.name.that.does.not.fit.on.a.single.manifest.line";
        assertEquals(parse(new CAPMetadata(PACKAGE, name, "0.0", Collections.emptyList()).toManifest()).getName(), name);

        // The //aid/ URI keeps the converter's uppercase hex, which the digit-only AIDs above cannot show
        CAPMetadata lettered = new CAPMetadata(new AID("A000000151ABCD"), "com.example.lettered", "1.0",
                Collections.singletonList(new CAPMetadata.Applet(new AID("A000000151ABCD01"), APPLET_CLASS)));
        assertTrue(new String(lettered.toManifest(), StandardCharsets.UTF_8).contains("Classic-Package-AID: //aid/A000000151/ABCD"));
        assertTrue(new String(lettered.toAppletXml(), StandardCharsets.UTF_8).contains("<applet-AID>//aid/A000000151/ABCD01</applet-AID>"));
    }

    @Test
    public void testLibrariesAndUnnamedClasses() throws Exception {
        CAPMetadata library = new CAPMetadata(PACKAGE, "com.example.lib", "1.0", Collections.emptyList());
        assertEquals(parse(library.toManifest()).getName(), "com.example.lib");
        assertTrue(parse(library.toManifest()).getApplets().isEmpty());
        // A library gets no applet.xml, and neither does an applet whose class nobody named
        assertThrows(IllegalStateException.class, library::toAppletXml);
        assertThrows(IllegalStateException.class, () -> new CAPMetadata(PACKAGE, "com.example.applet", "0.1",
                Collections.singletonList(new CAPMetadata.Applet(APPLET, null))).toAppletXml());
        // Naming an applet the package does not have is a mismatch
        assertThrows(IllegalArgumentException.class, () ->
                applet().withAppletClasses(Collections.singletonMap(new AID("A00000015100"), "com.example.Other")));
    }

    @Test
    public void testRejectsMalformedInput() throws Exception {
        // An ordinary JAR manifest describes no JavaCard package.
        assertThrows(IllegalArgumentException.class, () -> parse(crlf("Manifest-Version: 1.0", "Created-By: Maven JAR Plugin 3.4.1", "",
                "Name: com/example/Signed.class", "SHA-256-Digest: 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", "", "")));
        // Neither the package name nor the version can be left out.
        assertThrows(IllegalArgumentException.class, () -> parse(crlf("Manifest-Version: 1.0", "",
                "Name: com/example/wallet", "Java-Card-Package-AID: 0xa0:0x00:0x00:0x00:0x01", "", "")));
        assertThrows(IllegalArgumentException.class, () -> new CAPMetadata(PACKAGE, null, "0.1", Collections.emptyList()));
        // A malformed AID is rejected as it is read.
        assertThrows(IllegalArgumentException.class, () -> parse(crlf("Manifest-Version: 1.0", "",
                "Name: testapplets/empty", "Java-Card-Package-Name: testapplets.empty",
                "Java-Card-Package-AID: 0x01:0x02", "Java-Card-Package-Version: 1.0", "", "")));
        // Two packages in one manifest is not a shape this writes or reads.
        assertThrows(IllegalArgumentException.class, () -> parse(crlf("Manifest-Version: 1.0", "",
                "Name: com/example/one", "Java-Card-Package-Name: com.example.one",
                "Java-Card-Package-AID: 0x01:0x02:0x03:0x04:0x05", "Java-Card-Package-Version: 1.0", "",
                "Name: com/example/two", "Java-Card-Package-Name: com.example.two",
                "Java-Card-Package-AID: 0x01:0x02:0x03:0x04:0x06", "Java-Card-Package-Version: 1.0", "", "")));
    }
}
