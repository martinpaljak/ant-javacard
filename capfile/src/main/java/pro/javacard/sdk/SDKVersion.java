/*
 * Copyright (c) 2022-2024 Martin Paljak
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pro.javacard.sdk;

import java.util.*;

public enum SDKVersion {
    V211("2.1.1", "1.1", null, null),
    V212("2.1.2", "1.1", null, null),
    V221("2.2.1", "1.2", null, null),
    V222("2.2.2", "1.5", null, null),
    V301("3.0.1", "1.6", null, Arrays.asList(8, 11)),
    V304("3.0.4", "1.6", null, Arrays.asList(8, 11)),
    V305("3.0.5", "1.6", null, Arrays.asList(8, 11)),
    // NOTE: can't use EnumSet "recursively", thus turn the List into normal HashSet in constructor
    V310("3.1.0", "1.7", Arrays.asList(V304, V305), Arrays.asList(8, 11, 17)),
    V320("3.2.0", "1.7", Arrays.asList(V304, V305, V310), Arrays.asList(8, 11, 17)),
    V320_24_1("3.2.0", "1.7", Arrays.asList(V304, V305, V310, V320), Arrays.asList(11, 17)),
    V320_25_0("3.2.0", "1.8", Arrays.asList(V304, V305, V310, V320), Arrays.asList(8, 11, 17, 21)),
    V320_25_1("3.2.0", "1.8", Arrays.asList(V304, V305, V310, V320), Arrays.asList(8, 11, 17, 21));



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
        for (SDKVersion v : versions)
            if (this.equals(v))
                return true;
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
