// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.capfile;

import java.util.Arrays;

public final class AID {
    private static final String AID_URI = "//aid/";

    private final byte[] bytes;

    public AID(byte[] bytes) throws IllegalArgumentException {
        this(bytes, 0, bytes.length);
    }

    public AID(String str) throws IllegalArgumentException {
        this(HexUtils.hex2bin(str));
    }

    public AID(byte[] bytes, int offset, int length) throws IllegalArgumentException {
        if ((length < 5) || (length > 16)) {
            throw new IllegalArgumentException("AID must be between 5 and 16 bytes: " + length);
        }
        this.bytes = Arrays.copyOfRange(bytes, offset, offset + length);
    }

    // Any of the encodings a CAP file uses: plain hex, 0x01:0x02:... or //aid/<RID>/<PIX>
    public static AID fromString(Object s) {
        if (s instanceof String) {
            String str = ((String) s).trim();
            if (str.startsWith(AID_URI)) {
                str = str.substring(AID_URI.length()).replace("/", "");
            }
            // stringToBin handles the 0x/0X prefixes, separators and stray whitespace.
            return new AID(HexUtils.stringToBin(str));
        }
        throw new IllegalArgumentException("AID should be string");
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public int getLength() {
        return bytes.length;
    }

    @Override
    public String toString() {
        return HexUtils.bin2hex(bytes);
    }

    // The manifest 0x01:0x02:... form, also what the converter takes on its command line
    public String toColonHex() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("0x%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    // The //aid/<RID>/<PIX> URI form, split at the 5-byte RID
    String toAidUri() {
        String hex = toString().toLowerCase();
        int split = Math.min(10, hex.length());
        return AID_URI + hex.substring(0, split) + "/" + hex.substring(split);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AID) {
            return Arrays.equals(((AID) o).bytes, bytes);
        }
        return false;
    }
}
