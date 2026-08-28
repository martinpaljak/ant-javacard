// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT
package pro.javacard.zip;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class TestReproducibleZip {

    private static final byte[] DATA = "compress me, compress me, compress me".getBytes(StandardCharsets.UTF_8);

    private static Map<String, byte[]> sample() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("u.exp", "export".getBytes(StandardCharsets.UTF_8));
        entries.put(ReproducibleZip.MANIFEST, "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        entries.put("a.txt", new byte[0]);
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ReproducibleZip.write(bos, entries, ZipEntry.STORED, ReproducibleZip.FIXED_TIME);
        return bos.toByteArray();
    }

    private static List<String> names(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip));
        for (ZipEntry e = zis.getNextEntry(); e != null; e = zis.getNextEntry()) {
            names.add(e.getName());
        }
        return names;
    }

    @Test
    public void testWritesArchives() throws Exception {
        byte[] stored = zip(sample());
        assertEquals(names(stored), new ArrayList<>(sample().keySet()));
        assertEquals(names(zip(ReproducibleZip.sorted(sample(), "u.exp"))), List.of("u.exp", ReproducibleZip.MANIFEST, "a.txt"));
        assertEquals(names(zip(ReproducibleZip.leading(sample(), "a.txt", "nope"))), List.of("a.txt", "u.exp", ReproducibleZip.MANIFEST));
        // No extra field, and a stored entry carries its sizes in the local header
        assertEquals((stored[28] & 0xFF) | ((stored[29] & 0xFF) << 8), 0);
        // A container leads with the media type, uncompressed, at the offset file(1) reads
        String mime = "application/vnd.etsi.asic-e+zip";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ReproducibleZip.writeWithMimetype(bos, mime, sample(), ZipEntry.DEFLATED, ReproducibleZip.FIXED_TIME);
        assertEquals(new String(bos.toByteArray(), 38, mime.length(), StandardCharsets.US_ASCII), mime);
        // The media type is an entry like any other, so naming it twice is a duplicate
        assertThrows(ZipException.class, () -> ReproducibleZip.writeWithMimetype(new ByteArrayOutputStream(), mime,
                Collections.singletonMap(ReproducibleZip.MIMETYPE, new byte[0]), ZipEntry.DEFLATED, ReproducibleZip.FIXED_TIME));
    }

    @Test
    public void testEntriesAndTimes() throws Exception {
        LocalDateTime stamp = LocalDateTime.of(2011, 2, 3, 4, 5, 6);
        Path file = Files.createTempFile("entry", ".txt");
        file.toFile().deleteOnExit();
        Files.write(file, DATA);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            ReproducibleZip.entry(zos, "kept.txt", new ByteArrayInputStream(DATA), ZipEntry.DEFLATED, stamp);
            ReproducibleZip.entry(zos, "floored.txt", file, ZipEntry.STORED, LocalDateTime.of(1970, 1, 1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> ReproducibleZip.entry(zos, "s.txt", new ByteArrayInputStream(DATA), ZipEntry.STORED, stamp));
        }
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bos.toByteArray()));
        assertEquals(ReproducibleZip.timeOf(zis.getNextEntry()), stamp);
        assertEquals(ReproducibleZip.timeOf(zis.getNextEntry()), ReproducibleZip.FIXED_TIME);
        assertEquals(ReproducibleZip.epochTime("1234567890"), LocalDateTime.of(2009, 2, 13, 23, 31, 30));
    }

    @Test
    public void testArchiveComment() throws Exception {
        byte[] zip = zip(sample());
        byte[] comment = "an armored signature".getBytes(StandardCharsets.US_ASCII);
        byte[] signed = ZipComment.embed(zip, comment);
        assertEquals(ZipComment.comment(signed), comment);
        // Everything before the comment is left alone, and a second comment replaces the first
        assertEquals(ZipComment.payload(signed), zip);
        Path f = Files.createTempFile("zipcomment", ".zip");
        f.toFile().deleteOnExit();
        Files.write(f, zip);
        ZipComment.embed(f, comment);
        try (ZipComment.Archive archive = ZipComment.open(f)) {
            assertEquals(archive.comment(), comment);
            try (InputStream payload = archive.payload()) {
                assertEquals(payload.readAllBytes(), zip);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ZipComment.payload("not a zip".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> ZipComment.embed(zip, new byte[0x10000]));
    }
}
