// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import pro.javacard.capfile.AID;
import pro.javacard.capfile.CAPPackage;
import pro.javacard.sdk.JavaCardAPI;
import pro.javacard.sdk.JavaCardSDK;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The plan of one <cap> that JCCap.resolve() fills in
final class Build {
    JavaCardSDK sdk;                                        // the converter that runs
    JavaCardAPI target;                                     // the API the CAP is built against
    String javacTarget;                                     // "-source" and "-target" for the compile
    CAPPackage pkg;
    final Map<AID, String> applets = new LinkedHashMap<>();

    final List<Path> sources = new ArrayList<>();           // "sources" and "sources2" attributes
    String includes;                                        // "includes" attribute for every sources path
    String excludes;                                        // "excludes" attribute for every sources path
    final List<JCSources> nested = new ArrayList<>();       // <sources> elements with their own filters
    Path classes;                                           // null means a temporary folder

    final List<Path> importJars = new ArrayList<>();        // "jar" of each <import> for the compile classpath
    final List<Path> importExports = new ArrayList<>();     // where the export files of an <import> come from

    String cap;                                             // absolute and may still hold %-placeholders
    Path exp;                                               // export file tree root or null
    Path jca;                                               // null when not asked for
    Path jar;                                               // null when not asked for

    boolean verify;
    boolean debug;
    boolean strip;
    boolean ints;
    boolean exportmap;

    boolean compiles() {
        return !sources.isEmpty() || !nested.isEmpty();
    }

    String packageName() {
        return pkg.getName().orElseThrow(() -> new IllegalStateException("Package name is missing"));
    }

    // The name the converter gives every artifact of the package
    String name() {
        return Misc.lastName(packageName());
    }

    // 2.x CAP metadata does not carry the applet class
    String firstApplet() {
        return applets.isEmpty() ? null : applets.values().iterator().next();
    }
}
