// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT
package pro.javacard.zip;

import java.time.LocalDateTime;
import java.util.zip.ZipEntry;

final class ZIPEntryTime {

    private ZIPEntryTime() {
    }

    static void set(ZipEntry entry, LocalDateTime time) {
        entry.setTimeLocal(time);
    }

    static LocalDateTime get(ZipEntry entry) {
        return entry.getTimeLocal();
    }
}
