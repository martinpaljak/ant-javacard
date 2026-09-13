// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

// One row per SDK release: the API version it builds and the class files it deals in
public enum SDKRelease {
    // @formatter:off
    V211("2.1.1", APIVersion.V211, 45, 45, null, null),
    V212("2.1.2", APIVersion.V212, 45, 45, null, null),
    V221("2.2.1", APIVersion.V221, 46, 46, null, null),
    V222("2.2.2", APIVersion.V222, 49, 49, null, null),
    V301("3.0.1", APIVersion.V301, 50, 50, null, null),
    V304("3.0.4", APIVersion.V304, 50, 50, null, null),
    // The copyright year of the tools tells the v3.0.5 updates apart
    V305("3.0.5", APIVersion.V305, 51, 50, null, null),
    V305u1("3.0.5u1", APIVersion.V305, 51, 50, null, null),
    V305u2("3.0.5u2", APIVersion.V305, 51, 51, null, null),
    V305u3("3.0.5u3", APIVersion.V305, 51, 51, null, null),
    V305u4("3.0.5u4", APIVersion.V305, 51, 51, null, null),
    V310("3.1.0", APIVersion.V310, 51, 51, APIVersion.V304, APIVersion.V305),
    V24_0("24.0", APIVersion.V320, 51, 51, APIVersion.V304, APIVersion.V320),
    V24_1("24.1", APIVersion.V320, 51, 55, APIVersion.V304, APIVersion.V320),
    V25_0("25.0", APIVersion.V320, 54, 52, APIVersion.V304, APIVersion.V320),
    V25_1("25.1", APIVersion.V320, 54, 52, APIVersion.V304, APIVersion.V320),
    V26_0("26.0", APIVersion.V320, 54, 52, APIVersion.V304, APIVersion.PREVIEW_FINAL);
    // @formatter:on

    // No v2.1.X SDK has a verifier with verifyCap
    public static final Set<SDKRelease> NO_VERIFIER = EnumSet.of(V211, V212);

    // These need com.oracle.javacard.stringproc.StringConstantsProcessor on the javac processorpath
    public static final Set<SDKRelease> STRING_PROCESSOR = EnumSet.range(V304, V310);

    // Their tools.jar logging.properties adds a FileHandler writing ~/java0.log.0
    public static final Set<SDKRelease> LOGS_TO_FILE = EnumSet.range(V301, V24_1);

    // Their converter needs jc.home to generate and compile SIO proxy classes
    public static final Set<SDKRelease> GENERATES_SIO_PROXIES = EnumSet.range(V301, V305u4);

    // A JDK writes and loads class files up to its own version plus this
    private static final int CLASS_FILE_OFFSET = 44;

    final String release;
    final APIVersion api;
    final int converter_class;  // Highest class file version the converter of the SDK eats
    final int tools_class;      // Class file version of the tools themselves
    final Set<APIVersion> targets;

    SDKRelease(String release, APIVersion api, int converter, int tools, APIVersion oldest, APIVersion newest) {
        this.release = release;
        this.api = api;
        this.converter_class = converter;
        this.tools_class = tools;
        this.targets = oldest == null ? EnumSet.noneOf(APIVersion.class) : EnumSet.range(oldest, newest);
    }

    @Override
    public String toString() {
        return release;
    }

    // The JavaCard API version this SDK builds against without being told otherwise
    public APIVersion api() {
        return api;
    }

    // API versions this SDK builds with -target
    public Set<APIVersion> targets() {
        return Collections.unmodifiableSet(targets);
    }

    // Oldest API version reachable with -target
    public Optional<APIVersion> oldestTarget() {
        return targets.stream().findFirst();
    }

    public boolean equalOrNewer(SDKRelease other) {
        return this.ordinal() >= other.ordinal();
    }

    private static int classFileVersion(int jdk) {
        return jdk + CLASS_FILE_OFFSET;
    }

    private static int javaVersion(int classFile) {
        return classFile - CLASS_FILE_OFFSET;
    }

    // Oldest JDK that can load the tools of this SDK
    public int oldestJDK() {
        return Math.max(8, javaVersion(tools_class));
    }

    // Newest JDK whose javac still writes what this converter takes: 1.5 and older went in JDK 9, 1.6 in 12, 1.7 in 20
    public int newestJDK() {
        if (converter_class >= 52) {
            return Integer.MAX_VALUE;
        }
        if (converter_class >= 51) {
            return 19;
        }
        if (converter_class >= 50) {
            return 11;
        }
        return 8;
    }

    // The best -source and -target for both this converter and the JDK
    public String javacTarget(int jdk) {
        int java = javaVersion(Math.min(converter_class, classFileVersion(jdk)));
        // javac spelled everything up to Java 8 as "1.x"
        return java > 8 ? Integer.toString(java) : "1." + java;
    }
}
