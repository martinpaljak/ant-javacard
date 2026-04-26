// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.capfile;

import java.util.Objects;
import java.util.Optional;

public final class CAPPackage {
    final AID aid;
    final int major;
    final int minor;
    final String name;

    public CAPPackage(AID aid, int major, int minor) {
        this(aid, major, minor, null);
    }

    public CAPPackage(AID aid, int major, int minor, String name) {
        this.aid = aid;
        this.major = major;
        this.minor = minor;
        this.name = name;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof CAPPackage) {
            CAPPackage o = (CAPPackage) other;
            return aid.equals(o.aid) && major == o.major && minor == o.minor;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(aid, major, minor);
    }

    @Override
    public String toString() {
        return String.format("%-32s v%d.%d %s", aid, major, minor, getName().orElse(WellKnownAID.getName(aid).orElse("(unknown)")));
    }

    public String getVersionString() {
        return String.format("%d.%d", major, minor);
    }

    public AID getAid() {
        return aid;
    }

    public int getMinor() {
        return minor;
    }

    public int getMajor() {
        return major;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }
}
