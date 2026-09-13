// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

module pro.javacard.sdk {
    requires transitive pro.javacard.capfile;
    requires transitive java.logging;

    exports pro.javacard.sdk;
}
