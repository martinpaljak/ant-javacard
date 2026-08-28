// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT
package pro.javacard.zip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Bytes depend only on entry names, order, contents and timestamps. Build with TZ=UTC.
public final class ReproducibleZip {

    // At the first DOS second java.util.zip attaches an extended timestamp read in the default zone
    public static final LocalDateTime FIXED_TIME = LocalDateTime.of(1980, 1, 1, 0, 0, 2);

    // Past 2099 java.util.zip attaches that same extended timestamp
    private static final LocalDateTime LATEST_TIME = LocalDateTime.of(2099, 12, 31, 23, 59, 58);

    // The entry a JAR's stream readers expect to lead the archive
    public static final String MANIFEST = "META-INF/MANIFEST.MF";

    // ODF and ASiC want this stored first with no extra field, at an offset file(1) reads
    public static final String MIMETYPE = "mimetype";

    private static final String SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH";

    private static final int BUFFER = 64 * 1024;

    private ReproducibleZip() {
    }

    // The only place here that reads the environment
    public static Optional<LocalDateTime> sourceDateEpoch() {
        String seconds = System.getenv(SOURCE_DATE_EPOCH);
        return seconds == null ? Optional.empty() : Optional.of(stamp(epochTime(seconds)));
    }

    // https://reproducible-builds.org/specs/source-date-epoch/ wants the output of date +%s and
    // a build that stops on anything else. Long.parseLong takes a sign and any Unicode digits.
    static LocalDateTime epochTime(String seconds) {
        try {
            if (!seconds.matches("[0-9]+")) {
                throw new NumberFormatException(seconds);
            }
            return timeOf(Instant.ofEpochSecond(Long.parseLong(seconds)));
        } catch (NumberFormatException | DateTimeException e) {
            throw new IllegalArgumentException(SOURCE_DATE_EPOCH + " is not a count of seconds: " + seconds, e);
        }
    }

    public static <T> Map<String, T> leading(Map<String, T> entries, String... first) {
        Map<String, T> ordered = new LinkedHashMap<String, T>();
        for (String name : first) {
            if (entries.containsKey(name)) {
                ordered.put(name, entries.get(name));
            }
        }
        for (Map.Entry<String, T> e : entries.entrySet()) {
            ordered.putIfAbsent(e.getKey(), e.getValue());
        }
        return ordered;
    }

    // Names sort by their characters, which past the Basic Multilingual Plane is not byte order
    public static <T> Map<String, T> sorted(Map<String, T> entries, String... first) {
        return leading(new TreeMap<String, T>(entries), first);
    }

    // Order is part of the output: a map keeping none of its own gives different bytes per run
    public static void write(OutputStream out, Map<String, byte[]> entries, int method, LocalDateTime time) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            entries(zos, entries, method, stamp(time));
        }
    }

    public static void writeWithMimetype(OutputStream out, String mimetype, Map<String, byte[]> entries, int method, LocalDateTime time) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            LocalDateTime stamp = stamp(time);
            entry(zos, MIMETYPE, mimetype.getBytes(StandardCharsets.US_ASCII), ZipEntry.STORED, stamp);
            entries(zos, entries, method, stamp);
        }
    }

    private static void entries(ZipOutputStream out, Map<String, byte[]> entries, int method, LocalDateTime time) throws IOException {
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            entry(out, e.getKey(), e.getValue(), method, time);
        }
    }

    public static void entry(ZipOutputStream out, String name, byte[] data, int method, LocalDateTime time) throws IOException {
        ZipEntry entry = header(name, method, time);
        if (method == ZipEntry.STORED) {
            entry.setCrc(crc32(data));
            entry.setSize(data.length);
        }
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    // A stored entry is read twice, once for the crc and length its local header carries
    public static void entry(ZipOutputStream out, String name, Path file, int method, LocalDateTime time) throws IOException {
        ZipEntry entry = header(name, method, time);
        if (method == ZipEntry.STORED) {
            entry.setCrc(crc32(file));
            entry.setSize(Files.size(file));
        }
        out.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(file)) {
            copy(in, out);
        }
        out.closeEntry();
    }

    public static void entry(ZipOutputStream out, String name, InputStream data, int method, LocalDateTime time) throws IOException {
        if (method != ZipEntry.DEFLATED) {
            throw new IllegalArgumentException("Only a deflated entry can be written from a stream: " + name);
        }
        out.putNextEntry(header(name, method, time));
        copy(data, out);
        out.closeEntry();
    }

    private static ZipEntry header(String name, int method, LocalDateTime time) {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method);
        // setTime() reads back through the default zone, so converting with that zone cancels out
        entry.setTime(clamp(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return entry;
    }

    // Reads to the end of the stream, not to the first empty read
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER];
        for (int n; (n = in.read(buffer)) != -1; ) {
            if (n > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    // getTime() decoded in the default zone, so reading it back there recovers what the archive holds
    public static LocalDateTime timeOf(ZipEntry source) {
        long millis = source.getTime();
        if (millis == -1) {
            return FIXED_TIME;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    public static LocalDateTime timeOf(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    // The time to stamp entries with, said aloud when a zip cannot hold the one asked for
    private static LocalDateTime stamp(LocalDateTime time) {
        LocalDateTime held = clamp(time);
        if (!held.equals(time)) {
            System.err.println(time + " is outside what a zip can hold, writing " + held);
        }
        return held;
    }

    private static LocalDateTime clamp(LocalDateTime time) {
        if (time.isBefore(FIXED_TIME)) {
            return FIXED_TIME;
        }
        return time.isAfter(LATEST_TIME) ? LATEST_TIME : time;
    }

    private static long crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static long crc32(Path file) throws IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER];
            for (int n; (n = in.read(buffer)) != -1; ) {
                if (n > 0) {
                    crc.update(buffer, 0, n);
                }
            }
        }
        return crc.getValue();
    }
}
