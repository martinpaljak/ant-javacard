// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT
package pro.javacard.zip;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.ZipEntry;

final class ZIPEntryTime {

    private ZIPEntryTime() {
    }

    // setTime() encodes the DOS time in the default zone
    static void set(ZipEntry entry, LocalDateTime time) {
        entry.setTime(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    // getTime() decodes the DOS time in the default zone
    static LocalDateTime get(ZipEntry entry) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.getTime()), ZoneId.systemDefault());
    }
}
