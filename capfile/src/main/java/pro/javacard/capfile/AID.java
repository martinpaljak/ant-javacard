// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.capfile;

import java.util.Arrays;

public final class AID {
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

    public static AID fromString(Object s) {
        if (s instanceof String) {
            return new AID(HexUtils.stringToBin((String) s));
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
