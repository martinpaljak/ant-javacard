// SPDX-FileCopyrightText: 2022 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import java.util.Arrays;
import java.util.Optional;

// JavaCard API version a CAP file is built against
public enum APIVersion {
    V211("2.1.1"),
    V212("2.1.2"),
    V221("2.2.1"),
    V222("2.2.2"),
    V301("3.0.1"),
    V304("3.0.4"),
    V305("3.0.5"),
    V310("3.1.0"),
    V320("3.2.0"),
    // "Java Card API 3.2 with Preview Features" and its subset marked "Final"
    PREVIEW("preview"),
    PREVIEW_FINAL("preview-final");

    final String v;

    APIVersion(String v) {
        this.v = v;
    }

    @Override
    public String toString() {
        return this.v;
    }

    public boolean isOneOf(APIVersion... versions) {
        for (APIVersion v : versions) {
            if (this.equals(v)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<APIVersion> fromVersion(String versionString) {
        return Arrays.stream(values()).filter(ver -> ver.v.equals(versionString)).findFirst();
    }

    public boolean equalOrNewer(APIVersion other) {
        return this.ordinal() >= other.ordinal();
    }
}
