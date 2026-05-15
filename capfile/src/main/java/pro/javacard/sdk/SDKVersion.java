// SPDX-FileCopyrightText: 2022 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import java.util.*;

public enum SDKVersion {
    V211("2.1.1", "1.1", null, null),
    V212("2.1.2", "1.1", null, null),
    V221("2.2.1", "1.2", null, null),
    V222("2.2.2", "1.5", null, null),
    V301("3.0.1", "1.6", null, Arrays.asList(8, 11)),
    V304("3.0.4", "1.6", null, Arrays.asList(8, 11)),
    V305("3.0.5", "1.7", null, Arrays.asList(8, 11, 17)),
    // NOTE: can't use EnumSet "recursively", thus turn the List into normal HashSet in constructor
    V310("3.1.0", "1.7", Arrays.asList(V304, V305), Arrays.asList(8, 11, 17)),
    V320("3.2.0", "1.7", Arrays.asList(V304, V305, V310), Arrays.asList(8, 11, 17)),
    V320_24_1("3.2.0", "1.7", Arrays.asList(V304, V305, V310, V320), Arrays.asList(11, 17)),
    V320_25_0("3.2.0", "1.8", Arrays.asList(V304, V305, V310, V320), Arrays.asList(8, 11, 17, 21, 25)),
    V320_25_1("3.2.0", "1.8", Arrays.asList(V304, V305, V310, V320), Arrays.asList(8, 11, 17, 21, 25)),
    // Preview targets shipped alongside 3.2.0 (spec: "Java Card API 3.2 with Preview Features", aka 3.2.0+preview).
    // -final is the subset of preview features marked "Final" (frozen); reached via target() from v26.0 only.
    V320_PREVIEW("preview", "1.8", null, Arrays.asList(8, 11, 17, 21, 25)),
    V320_PREVIEW_FINAL("preview-final", "1.8", null, Arrays.asList(8, 11, 17, 21, 25)),
    V320_26_0("3.2.0", "1.8", Arrays.asList(V304, V305, V310, V320, V320_PREVIEW, V320_PREVIEW_FINAL), Arrays.asList(8, 11, 17, 21, 25));



    final String v;
    final String class_file_target;  // This indicates the highest class file version edible by SDK-s converter
    final Set<Integer> jdks;
    final Set<SDKVersion> targets;

    SDKVersion(String v, String classfile, Collection<SDKVersion> targets, List<Integer> jdks) {
        this.v = v;
        this.class_file_target = classfile;
        this.targets = targets == null ? new HashSet<>() : new HashSet<>(targets);
        this.jdks = new HashSet<>(jdks == null ? Arrays.asList(8) : jdks);
    }

    @Override
    public String toString() {
        return this.v;
    }

    public Set<SDKVersion> targets() {
        return Collections.unmodifiableSet(this.targets);
    }

    public boolean isOneOf(SDKVersion... versions) {
        for (SDKVersion v : versions) {
            if (this.equals(v)) {
                return true;
            }
        }
        return false;
    }

    public String javaVersion() {
        return class_file_target;
    }

    public Set<Integer> jdkVersions() {
        return Collections.unmodifiableSet(jdks);
    }

    public static Optional<SDKVersion> fromVersion(String versionString) {
        return Arrays.stream(values()).filter(ver -> ver.v.equals(versionString)).findFirst();
    }

    public boolean equalOrNewer(SDKVersion other) {
        return this.ordinal() >= other.ordinal();
    }
}
