// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT
package pro.javacard.zip;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

// A signature fits in the archive comment; the signable payload is the archive with an empty one.
// Nothing is mapped: a mapping outlives the handle and can block replacing the file.
public final class ZipComment {

    private static final int EOCD_SIG = 0x06054b50;
    private static final int EOCD_MIN = 22;       // record size without comment
    private static final int MAX_COMMENT = 0xFFFF;
    private static final int LEN_OFFSET = 20;     // comment length field within the record

    private ZipComment() {
    }

    public static byte[] payload(byte[] file) {
        int eocd = findEocd(wrap(file), 0, file.length);
        byte[] p = Arrays.copyOf(file, eocd + EOCD_MIN);
        p[eocd + LEN_OFFSET] = 0;
        p[eocd + LEN_OFFSET + 1] = 0;
        return p;
    }

    public static byte[] comment(byte[] file) {
        ByteBuffer f = wrap(file);
        int eocd = findEocd(f, 0, file.length);
        return Arrays.copyOfRange(file, eocd + EOCD_MIN, eocd + EOCD_MIN + commentLength(f, eocd));
    }

    public static byte[] embed(byte[] file, byte[] comment) {
        byte[] p = payload(file);
        byte[] out = Arrays.copyOf(p, p.length + checked(comment).length);
        System.arraycopy(length(comment), 0, out, p.length - 2, 2);
        System.arraycopy(comment, 0, out, p.length, comment.length);
        return out;
    }

    // Comment and payload come from one handle, so a verifier sees one file
    public static Archive open(Path zip) throws IOException {
        return new Archive(zip);
    }

    public static final class Archive implements Closeable {

        private final FileChannel ch;
        private final long size;
        private final long eocd;

        private Archive(Path zip) throws IOException {
            ch = FileChannel.open(zip, StandardOpenOption.READ);
            try {
                size = ch.size();
                eocd = eocd(ch, size);
            } catch (IOException | RuntimeException e) {
                ch.close();
                throw e;
            }
        }

        public byte[] comment() throws IOException {
            byte[] comment = new byte[(int) (size - eocd - EOCD_MIN)];
            ByteBuffer into = ByteBuffer.wrap(comment);
            while (into.hasRemaining()) {
                if (ch.read(into, eocd + EOCD_MIN + into.position()) < 0) {
                    throw new EOFException("Truncated while reading the comment of " + size + " bytes");
                }
            }
            return comment;
        }

        // One payload at a time, read from the beginning. Closing it leaves the archive open.
        public InputStream payload() throws IOException {
            ch.position(0);
            return new SequenceInputStream(new Head(Channels.newInputStream(ch), eocd + LEN_OFFSET),
                    new ByteArrayInputStream(new byte[2]));
        }

        @Override
        public void close() throws IOException {
            ch.close();
        }
    }

    // Rewrites the tail in place, which a mapping cannot do: it cannot change the length of a file
    public static void embed(Path zip, byte[] comment) throws IOException {
        try (FileChannel ch = FileChannel.open(zip, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long eocd = eocd(ch, ch.size());
            byte[] tail = new byte[2 + checked(comment).length];
            System.arraycopy(length(comment), 0, tail, 0, 2);
            System.arraycopy(comment, 0, tail, 2, comment.length);
            ch.truncate(eocd + EOCD_MIN);
            for (ByteBuffer b = ByteBuffer.wrap(tail); b.hasRemaining(); ) {
                ch.write(b, eocd + LEN_OFFSET + b.position());
            }
        }
    }

    // Offset of the end of central directory record within the file
    private static long eocd(FileChannel ch, long size) throws IOException {
        long start = tail(size);
        return start + findEocd(tailOf(ch, start, size), start, size);
    }

    // Offset of the end of central directory record in the buffer, searched from the end
    private static int findEocd(ByteBuffer f, long start, long size) {
        int min = (int) Math.max(0, size - EOCD_MIN - MAX_COMMENT - start);
        for (int i = f.limit() - EOCD_MIN; i >= min; i--) {
            if (f.getInt(i) == EOCD_SIG && start + i + EOCD_MIN + commentLength(f, i) == size) {
                return i;
            }
        }
        throw new IllegalArgumentException("Not a ZIP file: no end of central directory record");
    }

    // Where a comment of the largest size a zip can hold would begin
    private static long tail(long size) {
        return Math.max(0, size - EOCD_MIN - MAX_COMMENT);
    }

    private static int commentLength(ByteBuffer f, int eocd) {
        return f.getShort(eocd + LEN_OFFSET) & 0xFFFF;
    }

    private static byte[] length(byte[] comment) {
        return new byte[]{(byte) (comment.length & 0xFF), (byte) ((comment.length >> 8) & 0xFF)};
    }

    private static byte[] checked(byte[] comment) {
        if (comment.length > MAX_COMMENT) {
            throw new IllegalArgumentException("Comment too large: " + comment.length);
        }
        return comment;
    }

    // Everything from "start" to the end of the file, which is at most one comment record
    private static ByteBuffer tailOf(FileChannel ch, long start, long size) throws IOException {
        ByteBuffer end = ByteBuffer.allocate((int) (size - start));
        while (end.hasRemaining()) {
            if (ch.read(end, start + end.position()) < 0) {
                throw new EOFException("Truncated while reading the end of " + size + " bytes");
            }
        }
        end.flip();
        return wrap(end);
    }

    private static ByteBuffer wrap(byte[] file) {
        return wrap(ByteBuffer.wrap(file));
    }

    // Every field of a zip is little endian
    private static ByteBuffer wrap(ByteBuffer b) {
        return b.order(ByteOrder.LITTLE_ENDIAN);
    }

    // The first "left" bytes of a stream. Closing one does not close the archive it reads from.
    private static final class Head extends FilterInputStream {

        private long left;

        Head(InputStream in, long limit) {
            super(in);
            left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left == 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                left--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left == 0) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }

        @Override
        public void close() {
        }
    }
}
