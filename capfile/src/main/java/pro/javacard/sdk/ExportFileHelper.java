// SPDX-FileCopyrightText: 2024 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import pro.javacard.capfile.HexUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// Export file format: JCVM Spec v3.2, Chapter 5 "The Export File Format"
public final class ExportFileHelper {

    // JCVM 5.5: "The magic item contains the magic number identifying the ExportFile format; it has the value 0x00FACADE."
    static final int MAGIC = 0x00FACADE;

    // JCVM 5.6, Table 5-1: Export File Constant Pool Tags
    static final int TAG_UTF8 = 1;
    static final int TAG_INTEGER = 3;
    static final int TAG_CLASSREF = 7;
    static final int TAG_PACKAGE = 13;

    public enum ExportFileVersion {
        V21,
        V22,
        V23
    }

    public static final class PackageInfo {
        private final ExportFileVersion version;
        private final String name;
        private final byte[] aid;
        private final int major;
        private final int minor;
        private final boolean library;

        PackageInfo(ExportFileVersion version, String name, byte[] aid,
                    int major, int minor, boolean library) {
            this.version = version;
            this.name = name;
            this.aid = aid.clone();
            this.major = major;
            this.minor = minor;
            this.library = library;
        }

        public ExportFileVersion getVersion() {
            return version;
        }

        public String getName() {
            return name;
        }

        public byte[] getAid() {
            return aid.clone();
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public String getPackageVersion() {
            return String.format("%d.%d", major, minor);
        }

        public boolean isLibrary() {
            return library;
        }

        @Override
        public String toString() {
            return String.format("%s %s%s v%s (%s)", name, HexUtils.bin2hex(aid), library ? " library" : "", getPackageVersion(), version);
        }
    }

    private ExportFileHelper() {
    }

    public static PackageInfo parsePackage(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return parsePackage(in);
        }
    }

    public static PackageInfo parsePackage(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        // JCVM 5.5: magic (u4)
        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException(String.format("Bad magic: 0x%08X", magic));
        }

        // JCVM 5.5: minor_version (u1), major_version (u1)
        byte fileMinor = dis.readByte();
        byte fileMajor = dis.readByte();
        if (fileMajor != 2) {
            throw new IllegalArgumentException("Invalid export file major version: " + fileMajor);
        }
        ExportFileVersion version = parseFileVersion(fileMinor);

        // JCVM 5.5: constant_pool_count (u2)
        int cpCount = dis.readUnsignedShort();
        // JCVM 5.6: constant_pool[]
        Object[] pool = new Object[cpCount];

        // Store all CONSTANT_Package entries by index, since this_package
        // tells us which one is the actual exported package
        Map<Integer, int[]> pkgEntries = new HashMap<>(); // index -> [flags, nameIndex, minor, major]
        Map<Integer, byte[]> pkgAids = new HashMap<>();   // index -> aid

        for (int i = 0; i < cpCount; i++) {
            int tag = dis.readUnsignedByte();
            switch (tag) {
                case TAG_UTF8: {
                    // JCVM 5.6.4: length (u2), bytes[length]
                    int len = dis.readUnsignedShort();
                    byte[] bytes = new byte[len];
                    dis.readFully(bytes);
                    pool[i] = new String(bytes, "UTF-8");
                    break;
                }
                case TAG_INTEGER: {
                    // JCVM 5.6.3: bytes (u4)
                    dis.readInt();
                    break;
                }
                case TAG_CLASSREF: {
                    // JCVM 5.6.2: name_index (u2)
                    dis.readUnsignedShort();
                    break;
                }
                case TAG_PACKAGE: {
                    // JCVM 5.6.1: flags (u1), name_index (u2),
                    //   minor_version (u1), major_version (u1), aid_length (u1), aid[aid_length]
                    int flags = dis.readUnsignedByte();
                    int nameIndex = dis.readUnsignedShort();
                    int minor = dis.readUnsignedByte();
                    int major = dis.readUnsignedByte();
                    int aidLen = dis.readUnsignedByte();
                    byte[] aid = new byte[aidLen];
                    dis.readFully(aid);
                    pkgEntries.put(i, new int[]{flags, nameIndex, minor, major});
                    pkgAids.put(i, aid);
                    break;
                }
                default:
                    throw new IllegalArgumentException(String.format("Unknown constant pool tag: %d at index %d", tag, i));
            }
        }

        // JCVM 5.5: this_package (u2) - index into constant pool identifying the exported package
        int thisPackage = dis.readUnsignedShort();

        if (!pkgEntries.containsKey(thisPackage)) {
            throw new IllegalArgumentException(String.format("this_package index %d does not point to a CONSTANT_Package", thisPackage));
        }

        int[] pkg = pkgEntries.get(thisPackage);
        int pkgFlags = pkg[0];
        int pkgNameIndex = pkg[1];
        int pkgMinor = pkg[2];
        int pkgMajor = pkg[3];
        byte[] pkgAid = pkgAids.get(thisPackage);

        if (pkgNameIndex >= cpCount || !(pool[pkgNameIndex] instanceof String)) {
            throw new IllegalArgumentException("Invalid package name index: " + pkgNameIndex);
        }

        // JCVM 5.6.1: name_index -> CONSTANT_Utf8 with fully qualified package name using '/'
        String name = ((String) pool[pkgNameIndex]).replace('/', '.');

        // JCVM 5.6.1, Table 5-2: "If bit 0 of the flags item is set, this package is a library"
        boolean library = (pkgFlags & 0x01) != 0;

        return new PackageInfo(version, name, pkgAid, pkgMajor, pkgMinor, library);
    }

    // JCVM 5.5: "major version has the value 2", minor 1=v2.1, 2=v2.2, 3=v2.3
    private static ExportFileVersion parseFileVersion(int minor) {
        switch (minor) {
            case 1:
                return ExportFileVersion.V21;
            case 2:
                return ExportFileVersion.V22;
            case 3:
                return ExportFileVersion.V23;
            default:
                throw new IllegalArgumentException("Invalid export file minor version: " + minor);
        }
    }
}
