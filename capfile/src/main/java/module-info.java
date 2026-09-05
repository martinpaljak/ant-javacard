// SPDX-FileCopyrightText: 2024 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

module pro.javacard.capfile {
    requires java.xml;
    requires pro.javacard.zip;
    requires java.logging;

    exports pro.javacard.capfile;
    exports pro.javacard.sdk;
}
