/*
 * Copyright (c) 2024 Martin Paljak
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

/**
 * Central table-driven mapping for JavaCard SDK compatibility with JDK versions
 * and backwards targeting support.
 * 
 * ## JavaCard SDK Compatibility Matrix
 * 
 * | SDK Version | Java Version | JDK Range | Multitarget | Can Target |
 * |-------------|--------------|-----------|-------------|------------|
 * | 2.1.1       | 1.1          | 1-8       | No          | -          |
 * | 2.1.2       | 1.1          | 1-8       | No          | -          |
 * | 2.2.1       | 1.2          | 1-8       | No          | -          |
 * | 2.2.2       | 1.5          | 1-8       | No          | -          |
 * | 3.0.1       | 1.6          | 1-11      | No          | -          |
 * | 3.0.4       | 1.6          | 1-11      | No          | -          |
 * | 3.0.5       | 1.6          | 1-11      | No          | -          |
 * | 3.1.0       | 1.7          | 1-17      | Yes         | 3.0.4, 3.0.5, 3.1.0 |
 * | 3.2.0       | 1.7          | 8-17      | Yes         | 3.0.4, 3.0.5, 3.1.0, 3.2.0 |
 * | 3.2.0_24.1  | 1.7          | 11-17     | Yes         | 3.0.4, 3.0.5, 3.1.0, 3.2.0 |
 * | 3.2.0_25.0  | 1.8          | 8+        | Yes         | 3.0.4, 3.0.5, 3.1.0, 3.2.0 |
 * 
 * Note: "Multitarget" indicates if the SDK can target older JavaCard versions for backwards compatibility.
 * "Can Target" lists the JavaCard API versions that can be targeted when using modern SDKs.
 */
public final class SDKCompatibilityTable {

    // Private constructor to prevent instantiation
    private SDKCompatibilityTable() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static class CompatibilityEntry {
        private final SDKVersion sdkVersion;
        private final String javaClassFileVersion;
        private final int minJDK;
        private final int maxJDK;
        private final Set<SDKVersion> canTarget;
        private final boolean isMultitarget;

        public CompatibilityEntry(SDKVersion sdkVersion, String javaClassFileVersion, 
                                int minJDK, int maxJDK, boolean isMultitarget, SDKVersion... canTarget) {
            this.sdkVersion = sdkVersion;
            this.javaClassFileVersion = javaClassFileVersion;
            this.minJDK = minJDK;
            this.maxJDK = maxJDK;
            this.isMultitarget = isMultitarget;
            this.canTarget = canTarget.length > 0 ? EnumSet.of(canTarget[0], canTarget) : EnumSet.noneOf(SDKVersion.class);
        }

        public SDKVersion sdk() { return sdkVersion; }
        public String javaVersion() { return javaClassFileVersion; }
        public int minJDK() { return minJDK; }
        public int maxJDK() { return maxJDK; }
        public Set<SDKVersion> targets() { return Collections.unmodifiableSet(canTarget); }
        public boolean isMultitarget() { return isMultitarget; }

        public boolean isJDKCompatible(int jdkVersion) {
            return jdkVersion >= minJDK && jdkVersion <= maxJDK;
        }

        public boolean canTargetVersion(SDKVersion targetVersion) {
            return canTarget.contains(targetVersion);
        }
    }

    // Central compatibility table
    private static final Map<SDKVersion, CompatibilityEntry> COMPATIBILITY_TABLE = new EnumMap<>(SDKVersion.class);

    static {
        // JavaCard 2.x SDK versions
        COMPATIBILITY_TABLE.put(SDKVersion.V211, new CompatibilityEntry(
            SDKVersion.V211, "1.1", 1, 8, false));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V212, new CompatibilityEntry(
            SDKVersion.V212, "1.1", 1, 8, false));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V221, new CompatibilityEntry(
            SDKVersion.V221, "1.2", 1, 8, false));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V222, new CompatibilityEntry(
            SDKVersion.V222, "1.5", 1, 8, false));

        // JavaCard 3.0.x SDK versions  
        COMPATIBILITY_TABLE.put(SDKVersion.V301, new CompatibilityEntry(
            SDKVersion.V301, "1.6", 1, 11, false));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V304, new CompatibilityEntry(
            SDKVersion.V304, "1.6", 1, 11, false));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V305, new CompatibilityEntry(
            SDKVersion.V305, "1.6", 1, 11, false));

        // JavaCard 3.1.0 SDK version - supports targeting older versions
        COMPATIBILITY_TABLE.put(SDKVersion.V310, new CompatibilityEntry(
            SDKVersion.V310, "1.7", 1, 17, true, 
            SDKVersion.V304, SDKVersion.V305, SDKVersion.V310));

        // JavaCard 3.2.0 SDK versions - supports targeting older versions
        COMPATIBILITY_TABLE.put(SDKVersion.V320, new CompatibilityEntry(
            SDKVersion.V320, "1.7", 8, 17, true,
            SDKVersion.V304, SDKVersion.V305, SDKVersion.V310, SDKVersion.V320));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V320_24_1, new CompatibilityEntry(
            SDKVersion.V320_24_1, "1.7", 11, 17, true,
            SDKVersion.V304, SDKVersion.V305, SDKVersion.V310, SDKVersion.V320));
        
        COMPATIBILITY_TABLE.put(SDKVersion.V320_25_0, new CompatibilityEntry(
            SDKVersion.V320_25_0, "1.8", 8, Integer.MAX_VALUE, true,
            SDKVersion.V304, SDKVersion.V305, SDKVersion.V310, SDKVersion.V320));
    }

    public static CompatibilityEntry getCompatibility(SDKVersion version) {
        CompatibilityEntry entry = COMPATIBILITY_TABLE.get(version);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown SDK version: " + version);
        }
        return entry;
    }

    public static String getJavaClassFileVersion(SDKVersion version) {
        return getCompatibility(version).javaVersion();
    }

    public static boolean isJDKCompatible(SDKVersion sdkVersion, int jdkVersion) {
        return getCompatibility(sdkVersion).isJDKCompatible(jdkVersion);
    }

    public static boolean canTarget(SDKVersion sourceSDK, SDKVersion targetSDK) {
        return getCompatibility(sourceSDK).canTargetVersion(targetSDK);
    }

    public static boolean isMultitarget(SDKVersion version) {
        return getCompatibility(version).isMultitarget();
    }

    public static int getMinJDKVersion(SDKVersion version) {
        return getCompatibility(version).minJDK();
    }

    public static int getMaxJDKVersion(SDKVersion version) {
        return getCompatibility(version).maxJDK();
    }

    public static Set<SDKVersion> getTargetableVersions(SDKVersion version) {
        return getCompatibility(version).targets();
    }

    public static String getJDKCompatibilityDescription(SDKVersion version) {
        CompatibilityEntry entry = getCompatibility(version);
        if (entry.maxJDK() == Integer.MAX_VALUE) {
            return String.format("JDK %d+", entry.minJDK());
        } else {
            return String.format("JDK %d-%d", entry.minJDK(), entry.maxJDK());
        }
    }
}