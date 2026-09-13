// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import pro.javacard.capfile.AID;
import pro.javacard.capfile.CAPPackage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Handler;

// Talking to the converter of an SDK: what to run, with which arguments.
public final class Converter {
    private final JavaCardSDK sdk;
    private final JavaCardAPI target;
    private final Map<AID, String> applets = new LinkedHashMap<>();

    private Path outputDir;
    private Path classDir;
    private Path exportTree;
    private boolean debug;
    private boolean verify;
    private boolean ints;
    private boolean exportmap;
    private boolean exp;
    private boolean jca;

    private Converter(JavaCardSDK sdk, JavaCardAPI target) {
        this.sdk = sdk;
        this.target = target;
    }

    public static Converter of(JavaCardSDK sdk, JavaCardAPI target) {
        return new Converter(sdk, target);
    }

    // Where the converter writes the package folder
    public Converter into(Path dir) {
        outputDir = dir;
        return this;
    }

    // Where the compiled class files are
    public Converter classes(Path dir) {
        classDir = dir;
        return this;
    }

    // A tree holding the export files of the imported packages
    public Converter exports(Path tree) {
        exportTree = tree;
        return this;
    }

    public Converter applets(Map<AID, String> classesByAID) {
        applets.putAll(classesByAID);
        return this;
    }

    public Converter debug(boolean yes) {
        debug = yes;
        return this;
    }

    public Converter verify(boolean yes) {
        verify = yes;
        return this;
    }

    public Converter ints(boolean yes) {
        ints = yes;
        return this;
    }

    public Converter exportmap(boolean yes) {
        exportmap = yes;
        return this;
    }

    // Also write the export file of the package
    public Converter exp(boolean yes) {
        exp = yes;
        return this;
    }

    // Also write the human readable assembly
    public Converter jca(boolean yes) {
        jca = yes;
        return this;
    }

    public String mainClass() {
        return sdk.converterClass();
    }

    // Both execution paths pass these arguments
    public List<String> arguments(CAPPackage pkg) {
        List<String> args = new ArrayList<>();

        args.add("-d");
        args.add(outputDir.toString());

        args.add("-classdir");
        args.add(classDir.toString());

        StringJoiner exportpath = new StringJoiner(File.pathSeparator);

        if (sdk.getRelease().targets().contains(target.version())) {
            args.add("-target");
            args.add(target.version().toString());
        } else {
            target.exportDir().ifPresent(dir -> exportpath.add(dir.toString()));
        }

        if (exportTree != null) {
            exportpath.add(exportTree.toString());
        }
        if (exportpath.length() > 0) {
            args.add("-exportpath");
            args.add(exportpath.toString());
        }

        args.add("-verbose");
        args.add("-nobanner");

        if (debug) {
            args.add("-debug");
        }
        if (!verify && !SDKRelease.NO_VERIFIER.contains(sdk.getRelease())) {
            args.add("-noverify");
        }
        // Skips generating and compiling SIO proxy classes
        if (SDKRelease.GENERATES_SIO_PROXIES.contains(sdk.getRelease())) {
            args.add("-useproxyclass");
        }
        if (ints) {
            args.add("-i");
        }
        if (exportmap) {
            args.add("-exportmap");
        }

        // One -out takes every artifact name
        args.add("-out");
        args.add("CAP");
        if (exp) {
            args.add("EXP");
        }
        if (jca) {
            args.add("JCA");
        }

        for (Map.Entry<AID, String> applet : applets.entrySet()) {
            args.add("-applet");
            args.add(applet.getKey().toColonHex());
            args.add(applet.getValue());
        }

        args.add(pkg.getName().orElseThrow(() -> new IllegalArgumentException("Package name is missing")));
        args.add(pkg.getAid().toColonHex());
        args.add(pkg.getVersionString());

        return args;
    }

    // A fresh classloader per run resets the static error counters of the converter
    public void convert(List<String> args, Handler handler) throws IOException, ReflectiveOperationException {
        SDKLogger.SDKLog capture = SDKLogger.capture(handler);
        try (URLClassLoader loader = sdk.getClassLoader()) {
            Class<?> harness = Class.forName("com.sun.javacard.converter.ConverterHarness", true, loader);
            Method conversion = harness.getMethod("startConversion", String[].class, Hashtable.class);
            conversion.invoke(null, new Object[]{args.toArray(new String[0]), null});
        } finally {
            capture.close();
        }
    }
}
