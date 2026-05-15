// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.FileSet;
import pro.javacard.capfile.CAPFile;
import pro.javacard.capfile.HexUtils;
import pro.javacard.sdk.JavaCardSDK;
import pro.javacard.sdk.OffCardVerifier;
import pro.javacard.sdk.SDKVersion;
import pro.javacard.sdk.VerifierError;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static pro.javacard.sdk.SDKVersion.*;

// <cap ...>...</cap> and actual execution of core task.
public class JCCap extends Task {

    static final String DEFAULT_CAP_NAME_TEMPLATE = "%n_%a_%h_%j_%J.cap";
    static final String DEFAULT_CAP_NAME_TEMPLATE_LIB = "%n_%a_%v_%h_%J.cap";

    private final String master_jckit_path;
    private JavaCardSDK jckit = null;
    private String classes_path = null;
    private String sources_path = null;
    private String sources2_path = null;
    private String includes = null;
    private String excludes = null;
    private String package_name = null;
    private byte[] package_aid = null;
    private String package_version = null;
    private final List<JCApplet> raw_applets = new ArrayList<>();
    private final List<JCImport> raw_imports = new ArrayList<>();
    private final List<JCSources> raw_sources = new ArrayList<>();
    private String output_cap = null;
    private String output_exp = null;
    private String output_jar = null;
    private String output_jca = null;
    private String jckit_path = null;
    private JavaCardSDK targetsdk = null;
    private String raw_targetsdk = null;

    private boolean verify = true;
    private boolean debug = false;
    private boolean strip = false;
    private boolean ints = false;
    private boolean exportmap = false;
    static final String _logconf;

    static final String LOGHACK = "_ANT_JAVACARD_LOGHACK";
    static final boolean loghack = Boolean.parseBoolean(System.getenv().getOrDefault(LOGHACK, "true"));

    static {
        if (loghack) {
            // Setting the java.util.logging configuration for convert task will prevent the creation of ~/java0.log.0 file
            Path logconf = Misc.makeTemp("logging").resolve("logging.properties");
            _logconf = logconf.toAbsolutePath().normalize().toString();
            try {
                Files.write(logconf, String.format("handlers = java.util.logging.ConsoleHandler%n.level = WARNING").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                System.err.println("Could not write temporary logging configuration: " + e.getMessage());
            }
        } else {
            _logconf = null;
            System.err.println("Loghack disabled");
        }
        if (System.getenv().containsKey(LOGHACK)) {
            System.err.println("log level at start: " + Logger.getLogger("").getLevel());
        }
    }

    public JCCap(String master_jckit_path) {
        this.master_jckit_path = master_jckit_path;
    }

    public void setJCKit(String msg) {
        jckit_path = msg;
    }

    public void setOutput(String msg) {
        output_cap = msg;
    }

    public void setExport(String msg) {
        output_exp = msg;
    }

    public void setJar(String msg) {
        output_jar = msg;
    }

    public void setJca(String msg) {
        output_jca = msg;
    }

    public void setPackage(String msg) {
        package_name = msg;
    }

    public void setClasses(String msg) {
        classes_path = msg;
    }

    public void setVersion(String msg) {
        package_version = msg;
    }

    public void setSources(String arg) {
        sources_path = arg;
    }

    public void setSources2(String arg) {
        sources2_path = arg;
    }

    public void setIncludes(String arg) {
        includes = arg;
    }

    public void setExcludes(String arg) {
        excludes = arg;
    }

    public void setVerify(boolean arg) {
        verify = arg;
    }

    public void setDebug(boolean arg) {
        debug = arg;
    }

    public void setStrip(boolean arg) {
        strip = arg;
    }

    public void setInts(boolean arg) {
        ints = arg;
    }

    public void setExportmap(boolean arg) {
        exportmap = arg;
    }

    public void setTargetsdk(String arg) {
        raw_targetsdk = arg;
    }

    public void setAID(String msg) {
        try {
            package_aid = Misc.stringToBin(msg);
            if (package_aid.length < 5 || package_aid.length > 16) {
                throw new BuildException(String.format("Package AID must be between 5 and 16 bytes: %s (%d)", HexUtils.bin2hex(package_aid), package_aid.length));
            }

        } catch (IllegalArgumentException e) {
            throw new BuildException("Not a correct package AID: " + e.getMessage());
        }
    }

    // Many applets inside one package
    public JCApplet createApplet() {
        JCApplet applet = new JCApplet();
        raw_applets.add(applet);
        return applet;
    }

    // Many imports inside one package
    public JCImport createImport() {
        JCImport imp = new JCImport();
        raw_imports.add(imp);
        return imp;
    }

    // To support usage from Gradle, where import is a reserved name
    public JCImport createJimport() {
        return this.createImport();
    }

    // Nested <sources path="" includes="" excludes=""/> elements
    public JCSources createSources() {
        JCSources src = new JCSources();
        raw_sources.add(src);
        return src;
    }

    private Optional<JavaCardSDK> findSDK() {
        // try local configuration first
        if (jckit_path != null) {
            return JavaCardSDK.detectSDK(getProject().resolveFile(jckit_path).toPath());
        }
        // then try the master configuration
        if (master_jckit_path != null) {
            return JavaCardSDK.detectSDK(getProject().resolveFile(master_jckit_path).toPath());
        }
        // now check via ant property
        String propPath = getProject().getProperty("jc.home");
        if (propPath != null) {
            return JavaCardSDK.detectSDK(getProject().resolveFile(propPath).toPath());
        }
        // finally via the environment
        String envPath = System.getenv("JC_HOME");
        if (envPath != null) {
            return JavaCardSDK.detectSDK(getProject().resolveFile(envPath).toPath());
        }
        // return null if no options
        return Optional.empty();
    }

    // Check that arguments are sufficient and do some DWIM
    private void check() {
        jckit = findSDK().orElseThrow(() -> new HelpingBuildException("No usable JavaCard SDK referenced"));

        log("INFO: using JavaCard " + jckit.getVersion() + " SDK in " + jckit.getRoot() + " with JDK " + Misc.getCurrentJDKVersion(), Project.MSG_INFO);

        if (raw_targetsdk != null) {
            Optional<SDKVersion> targetVersion = SDKVersion.fromVersion(raw_targetsdk);
            if (targetVersion.isPresent() && !jckit.getVersion().targets().isEmpty()) {
                SDKVersion target = targetVersion.get();
                if (jckit.getVersion().equals(target)) {
                    log("WARN: \"targetsdk\" ignored as it matches \"jckit\" version", Project.MSG_WARN);
                } else {
                    if (jckit.getVersion().targets().contains(target)) {
                        targetsdk = jckit.target(target);
                    } else {
                        throw new HelpingBuildException(String.format("Can not target JavaCard %s with JavaCard kit %s", target, jckit.getVersion()));
                    }
                }
            } else {
                // Resolve target
                targetsdk = JavaCardSDK.detectSDK(getProject().resolveFile(raw_targetsdk).toPath()).orElseThrow(() -> new HelpingBuildException("Invalid \"targetsdk\": " + raw_targetsdk));
                // NOTE: verification will fail, as 3.1.0 (applies to all "modern multi-target" SDK-s)
                // will require version 2.3 export files (only available as part of newer SDK-s).
                // Verification is default, so fail early.
                // This also means that using an older SDK as path reference will actually use export files from current multi-target SDK
                if (!jckit.getVersion().targets().isEmpty() && !targetsdk.getVersion().equalOrNewer(V304)) {
                    throw new HelpingBuildException(String.format("targetsdk %s is not compatible with jckit %s", targetsdk.getVersion(), jckit.getVersion()));
                }
            }
        }

        if (targetsdk == null) {
            targetsdk = jckit;
        } else {
            if (jckit.getRoot() != targetsdk.getRoot()) {
                log(String.format("INFO: targeting JavaCard %s SDK in %s", targetsdk.getVersion(), targetsdk.getRoot()), Project.MSG_INFO);
            } else {
                log("INFO: targeting JavaCard " + targetsdk.getVersion(), Project.MSG_INFO);
            }
        }

        // Nudge towards the latest SDK in the appropriate family
        SDKVersion recommended = targetsdk.getVersion().equalOrNewer(V304) ? V320_26_0 : V305;
        if (jckit.getVersion() != recommended) {
            String recommendedRelease = recommended == V320_26_0 ? "26.0" : "3.0.5";
            String kitHint = recommended == V320_26_0 ? "jc320v26.0_kit" : "jc305u4_kit";
            log(String.format("WARN: using JavaCard v%s SDK; latest recommended is v%s (%s)", jckit.getRelease(), recommendedRelease, kitHint), Project.MSG_WARN);
        }

        if (verify && targetsdk.getVersion().isOneOf(V211, V212)) {
            log("WARN: verification disabled - not supported when targeting JavaCard v" + targetsdk.getVersion(), Project.MSG_WARN);
            verify = false;
        }

        // Warn about deprecation in future
        if (sources_path != null && sources2_path != null) {
            log("WARN: sources2 is deprecated in favor of multiple paths in sources", Project.MSG_WARN);
        }

        // Nested <sources> and flat sources/sources2 attributes are mutually exclusive
        if (!raw_sources.isEmpty() && (sources_path != null || sources2_path != null)) {
            throw new HelpingBuildException("Can not use both nested <sources> elements and sources/sources2 attributes");
        }

        // Shorthand for simple small projects - use Maven conventions
        if (sources_path == null && classes_path == null && raw_sources.isEmpty()) {
            if (getProject().resolveFile("src/main/javacard").isDirectory()) {
                sources_path = "src/main/javacard";
            } else if (getProject().resolveFile("src/main/java").isDirectory()) {
                sources_path = "src/main/java";
            }
        }

        // sources or classes must be set
        if (sources_path == null && classes_path == null && raw_sources.isEmpty()) {
            throw new HelpingBuildException("Must specify \"sources\" or \"classes\"");
        }

        // Check package version
        if (package_version == null) {
            package_version = "0.0";
        } else {
            // Allowed values are 0..127
            if (!package_version.matches("^[0-9]{1,3}\\.[0-9]{1,3}$")) {
                throw new HelpingBuildException("Invalid package version: " + package_version);
            }
            if (Arrays.stream(package_version.split("\\.")).map(e -> Integer.parseInt(e, 10)).anyMatch(e -> (e < 0 || e > 127))) {
                throw new HelpingBuildException("Illegal package version value: " + package_version);
            }
        }

        // Check imports
        for (JCImport a : raw_imports) {
            if (a.jar != null && !getProject().resolveFile(a.jar).isFile()) {
                throw new BuildException("Import JAR does not exist: " + a.jar);
            }
            if (a.exps != null && !getProject().resolveFile(a.exps).isDirectory()) {
                throw new BuildException("Import EXP files folder does not exist: " + a.exps);
            }
        }

        // Check nested sources
        for (JCSources s : raw_sources) {
            if (s.path == null) {
                throw new BuildException("Nested <sources> element must have a \"path\" attribute");
            }
            if (!getProject().resolveFile(s.path).isDirectory()) {
                throw new BuildException("Sources path does not exist: " + s.path);
            }
        }

        // Construct applets and fill in missing bits from package info, if necessary
        int applet_counter = 0;
        for (JCApplet a : raw_applets) {
            // Keep count for automagic numbering
            applet_counter = applet_counter + 1;

            if (a.klass == null) {
                throw new HelpingBuildException("Applet class is missing");
            }
            // If package name is present, must match the applet
            if (package_name != null) {
                if (!a.klass.contains(".")) {
                    a.klass = String.format("%s.%s", package_name, a.klass);
                } else if (!a.klass.startsWith(package_name)) {
                    throw new HelpingBuildException(String.format("Applet class %s is not in package %s", a.klass, package_name));
                }
            } else {
                if (a.klass.contains(".")) {
                    String pkgname = a.klass.substring(0, a.klass.lastIndexOf("."));
                    log("INFO: setting package name to " + pkgname, Project.MSG_INFO);
                    package_name = pkgname;
                } else {
                    throw new HelpingBuildException("Applet must be in a package!");
                }
            }

            // If applet AID is present, must match the package AID
            if (package_aid != null) {
                if (a.aid != null) {
                    // RID-s must match
                    if (!Arrays.equals(Arrays.copyOf(package_aid, 5), Arrays.copyOf(a.aid, 5))) {
                        throw new HelpingBuildException("Package RID does not match Applet RID");
                    }
                } else {
                    // make "magic" applet AID from package_aid + counter
                    a.aid = Arrays.copyOf(package_aid, package_aid.length + 1);
                    a.aid[package_aid.length] = (byte) applet_counter;
                    log("INFO: generated applet AID: " + HexUtils.bin2hex(a.aid) + " for " + a.klass, Project.MSG_INFO);
                }
            } else {
                // if package AID is empty, just set it to the minimal from
                // applet
                if (a.aid != null) {
                    package_aid = Arrays.copyOf(a.aid, 5);
                } else {
                    throw new HelpingBuildException("Both package AID and applet AID are missing!");
                }
            }
        }

        // Check package AID
        if (package_aid == null) {
            throw new HelpingBuildException("Must specify package AID");
        }

        // Package name must be present if no applets
        if (raw_applets.isEmpty()) {
            if (package_name == null) {
                throw new HelpingBuildException("Must specify package name if no applets");
            }
            log(String.format("Building library from package %s (AID: %s)", package_name, HexUtils.bin2hex(package_aid)), Project.MSG_INFO);
        } else {
            log(String.format("Building CAP with %d applet%s from package %s (AID: %s)", applet_counter, applet_counter > 1 ? "s" : "", package_name, HexUtils.bin2hex(package_aid)), Project.MSG_INFO);
            for (JCApplet app : raw_applets) {
                log(String.format("%s %s", app.klass, HexUtils.bin2hex(app.aid)), Project.MSG_INFO);
            }
        }
        if (output_exp != null) {
            // Last component of the package
            String ln = Misc.lastName(package_name);
            output_jar = new File(output_exp, ln + ".jar").toString();
        }
        // Default output name
        if (output_cap == null) {
            output_cap = raw_applets.size() == 0 ? DEFAULT_CAP_NAME_TEMPLATE_LIB : DEFAULT_CAP_NAME_TEMPLATE;
        }
    }

    // To lessen the java.nio and apache.ant namespace clash...
    private org.apache.tools.ant.types.Path mkPath(String name) {
        if (name == null) {
            return new org.apache.tools.ant.types.Path(getProject());
        }
        return new org.apache.tools.ant.types.Path(getProject(), name);
    }

    private void compile() {
        Project project = getProject();
        setTaskName("compile");

        // construct javac task
        Javac j = new Javac();
        j.setProject(project);
        // See https://github.com/martinpaljak/ant-javacard/pull/96
        j.setEncoding("utf-8");
        j.setTaskName("compile");

        org.apache.tools.ant.types.Path sources = mkPath(null);

        if (!raw_sources.isEmpty()) {
            // Per-path includes/excludes via nested <sources> elements
            setTaskName("sources");
            Path mergedDir = Misc.makeTemp("sources-" + runIdentifier());
            for (JCSources src : raw_sources) {
                File srcDir = project.resolveFile(src.path);
                FileSet fs = new FileSet();
                fs.setDir(srcDir);
                fs.setProject(project);
                if (src.includes != null) {
                    fs.setIncludes(src.includes);
                }
                if (src.excludes != null) {
                    fs.setExcludes(src.excludes);
                }
                String[] matched = fs.getDirectoryScanner(project).getIncludedFiles();
                for (String rel : matched) {
                    Path from = srcDir.toPath().resolve(rel);
                    Path to = mergedDir.resolve(rel);
                    log("using " + from, Project.MSG_INFO);
                    try {
                        Path parent = to.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new BuildException("Failed to copy source file: " + from, e);
                    }
                }
            }
            setTaskName("compile");
            sources.append(mkPath(mergedDir.toAbsolutePath().toString()));
        } else {
            // Legacy: flat sources/sources2/includes/excludes attributes
            String pattern = Pattern.quote(File.pathSeparator);
            String[] sources_paths = sources_path.split(pattern);
            for (String path : sources_paths) {
                sources.append(mkPath(path));
            }

            // Old style - second folder
            if (sources2_path != null) {
                sources.append(mkPath(sources2_path));
            }

            if (includes != null) {
                j.setIncludes(includes);
            }

            if (excludes != null) {
                j.setExcludes(excludes);
            }
        }
        j.setSrcdir(sources);

        // We resolve files to compile based on the sources/includes/excludes parameters, so don't set sourcepath
        j.setSourcepath(new org.apache.tools.ant.types.Path(project, null));

        log("Compiling files from " + sources, Project.MSG_INFO);

        // determine output directory
        Path tmp;
        if (classes_path != null) {
            // if specified use that
            tmp = project.resolveFile(classes_path).toPath();
            if (!Files.exists(tmp)) {
                try {
                    Files.createDirectories(tmp);
                } catch (IOException e) {
                    throw new BuildException("Could not create classes folder " + tmp.toAbsolutePath());
                }
            }
        } else {
            // else generate temporary folder
            tmp = Misc.makeTemp("classes-" + runIdentifier());
            classes_path = tmp.toAbsolutePath().toString();
        }

        j.setDestdir(tmp.toFile());
        // See "Setting Java Compiler Options" in User Guide
        j.setDebug(true);
        j.setDebugLevel("lines,vars,source");

        // set the best option supported by jckit
        String javaVersion = jckit.getVersion().javaVersion();
        // Warn in human-readable way if Java not compatible with JC Kit
        // See https://github.com/martinpaljak/ant-javacard/issues/79
        int jdkver = Misc.getCurrentJDKVersion();

        if (!jckit.getVersion().jdkVersions().contains(jdkver)) {
            if (jdkver > 17 && !jckit.getVersion().isOneOf(V320_25_0, V320_25_1, V320_26_0)) {
                // JDK 21+ can only target 1.8 or higher; only kit v25.0+ converters accept that.
                throw new HelpingBuildException(String.format("JavaCard kit v25.0+ required for JDK %d. Use JDK 17 or upgrade to v26.0.", jdkver));
            } else if (jckit.getVersion().isOneOf(V211, V212, V221, V222) && jdkver > 8) {
                // 2.x converters max out at class 49 or lower; JDK 9+ minimum target is 1.6.
                throw new HelpingBuildException(String.format("JavaCard kit v%s requires JDK 8 (legacy 2.x). Upgrade to v26.0 for modern JDKs.", jckit.getRelease()));
            } else if (jdkver > 11 && !jckit.getVersion().isOneOf(V305, V310, V320, V320_24_1, V320_25_0, V320_25_1, V320_26_0)) {
                // JDK 17+ minimum target is 1.7; only kits whose converter accepts class 51 or above work.
                throw new HelpingBuildException(String.format("JDK %d incompatible with JavaCard kit v%s (converter caps at 1.6). Use JDK 11 or upgrade to v26.0.", jdkver, jckit.getRelease()));
            } else if (jdkver == 8 && jckit.getVersion().isOneOf(V320_24_1)) {
                // 24.1's tools.jar is class 55; JDK 8 JVM can't load it.
                throw new HelpingBuildException("JavaCard kit v24.1 requires JDK 11+ (tools.jar class 55). Use JDK 11/17 or upgrade to v26.0.");
            }
        }
        j.setTarget(javaVersion);
        j.setSource(javaVersion);

        j.setIncludeantruntime(false);
        j.createCompilerArg().setValue("-Xlint");
        j.createCompilerArg().setValue("-Xlint:-options");
        j.createCompilerArg().setValue("-Xlint:-serial");
        if (jckit.getVersion().isOneOf(V304, V305, V310)) {
            //-processor com.oracle.javacard.stringproc.StringConstantsProcessor \
            //                -processorpath "JCDK_HOME/lib/tools.jar;JCDK_HOME/lib/api_classic_annotations.jar" \
            j.createCompilerArg().setLine("-processor com.oracle.javacard.stringproc.StringConstantsProcessor");
            org.apache.tools.ant.types.Path pcp = new Javac().createClasspath();
            for (Path jar : jckit.getCompilerJars()) {
                pcp.append(mkPath(jar.toString()));
            }
            j.createCompilerArg().setLine("-processorpath \"" + pcp.toString() + "\"");
            j.createCompilerArg().setValue("-Xlint:all,-processing");
        }

        j.setFailonerror(true);
        j.setFork(true);
        j.setListfiles(true);

        // set classpath
        org.apache.tools.ant.types.Path cp = j.createClasspath();
        JavaCardSDK sdk = targetsdk == null ? jckit : targetsdk;
        for (Path jar : sdk.getApiJars()) {
            cp.append(mkPath(jar.toString()));
        }
        for (JCImport i : raw_imports) {
            // Support import clauses with only jar or exp values
            if (i.jar != null) {
                cp.append(mkPath(i.jar));
            }
        }
        j.execute();
    }

    private void addKitClasses(Java j) {
        // classpath to jckit bits
        org.apache.tools.ant.types.Path cp = j.createClasspath();
        for (Path jar : jckit.getToolJars()) {
            cp.append(mkPath(jar.toString()));
        }
        j.setClasspath(cp);
    }

    private void convert(Path applet_folder, Set<Path> exps) {
        setTaskName("convert");
        // construct java task
        Java j = new Java(this);
        j.setTaskName("convert");
        // XXX: JC 25.0 does not exit with error on conversion issues.
        j.setFailonerror(true);
        j.setFork(true);

        // add classpath for SDK tools
        addKitClasses(j);

        // set class depending on SDK
        if (jckit.getVersion().equalOrNewer(V301)) {
            j.setClassname("com.sun.javacard.converter.Main");

            // Don't create java0.log.0 files in home folder
            // As a Java process is executed, we need to store it in a config file
            if (loghack) {
                Environment.Variable jclog = new Environment.Variable();
                jclog.setKey("java.util.logging.config.file");
                jclog.setValue(_logconf);
                j.addSysproperty(jclog);
            }
            // XXX: See https://community.oracle.com/message/10452555
            // This is disabled, because for whatever reason, having jc.home property set, the above logging suppression does not work.
            // make all shows no need for it on macos either.
            //Environment.Variable jchome = new Environment.Variable();
            //jchome.setKey("jc.home");
            //jchome.setValue(jckit.getRoot().toString());
            //j.addSysproperty(jchome);
        } else {
            j.setClassname("com.sun.javacard.converter.Converter");
        }

        // output path
        j.createArg().setLine("-d '" + applet_folder + "'");

        // classes for conversion
        j.createArg().setLine("-classdir '" + classes_path + "'");

        // construct export path
        StringJoiner expstringbuilder = new StringJoiner(File.pathSeparator);

        // Add targetSDK export files or the -target option
        if (jckit.getVersion().targets().contains(targetsdk.getVersion())) {
            j.createArg().setLine("-target " + targetsdk.getVersion().toString());
        } else {
            expstringbuilder.add(targetsdk.getExportDir().toString());
        }

        // imports
        for (Path imp : exps) {
            expstringbuilder.add(imp.toString());
        }
        if (expstringbuilder.length() > 0) {
            j.createArg().setLine("-exportpath '" + expstringbuilder + "'");
        }

        // always be a little verbose
        j.createArg().setLine("-verbose");
        j.createArg().setLine("-nobanner");

        // simple options
        if (debug) {
            j.createArg().setLine("-debug");
        }
        if (!verify && !jckit.getVersion().isOneOf(V211, V212)) {
            j.createArg().setLine("-noverify");
        }
        if (jckit.getVersion().equalOrNewer(V301)) {
            j.createArg().setLine("-useproxyclass");
        }
        if (ints) {
            j.createArg().setLine("-i");
        }
        if (exportmap) {
            j.createArg().setLine("-exportmap");
        }

        // determine output types
        String outputs = "CAP";
        if (output_exp != null || (raw_applets.size() > 0 && verify)) {
            outputs += " EXP";
        }
        if (output_jca != null) {
            outputs += " JCA";
        }
        j.createArg().setLine("-out " + outputs);

        // define applets
        for (JCApplet app : raw_applets) {
            j.createArg().setLine("-applet " + Misc.hexAID(app.aid) + " " + app.klass);
        }

        // package properties
        j.createArg().setLine(String.format("%s %s %s", package_name, Misc.hexAID(package_aid), package_version));

        // report the command
        log("command: " + j.getCommandLine(), Project.MSG_DEBUG);

        // execute the converter
        j.execute();

    }

    // Return an identifier that uniquely identifies "this run", so that temporary
    // subfolder in $ANT_JAVACARD_TMP would be sufficiently scoped to a <cap/>
    private int runIdentifier() {
        return Objects.hashCode(this);
    }

    @Override
    public void execute() {
        Project project = getProject();
        setTaskName("javacard");

        // perform checks
        check();

        try {
            // Compile first if necessary
            if (sources_path != null || !raw_sources.isEmpty()) {
                compile();
            }

            // Create temporary folder and add to cleanup
            Path applet_folder = Misc.makeTemp("applet-" + runIdentifier());

            // Construct exportpath
            Set<Path> exps = new TreeSet<>();

            // add imports
            for (JCImport imp : raw_imports) {
                // Support import clauses with only jar or exp values
                final Path f;
                if (imp.exps != null) {
                    f = Paths.get(imp.exps).toAbsolutePath();
                } else {
                    try {
                        // Assume exp files in jar
                        f = Misc.makeTemp("imports-" + runIdentifier());
                        OffCardVerifier.extractExps(project.resolveFile(imp.jar).toPath(), f);
                    } catch (IOException e) {
                        throw new BuildException("Can not extract EXP files from JAR", e);
                    }
                }
                exps.add(f);
            }

            // perform conversion
            convert(applet_folder, exps);

            // Copy results
            // Last component of the package
            String ln = Misc.lastName(package_name);
            // directory of package
            String pkgPath = package_name.replace(".", File.separator);
            Path pkgDir = applet_folder.resolve(pkgPath);
            Path jcsrc = pkgDir.resolve("javacard");
            // Interesting paths inside the JC folder
            Path cap = jcsrc.resolve(ln + ".cap");
            Path exp = jcsrc.resolve(ln + ".exp");
            Path jca = jcsrc.resolve(ln + ".jca");

            // XXX: JC 25.0 does not exit with error on conversion issues, so manually check
            if (!Files.exists(cap)) {
                throw new BuildException("CAP file not generated, check conversion for errors!");
            }

            // Verify
            if (verify) {
                setTaskName("verify");
                OffCardVerifier verifier = OffCardVerifier.withSDK(jckit);
                // Add current export file
                exps.add(exp);
                exps.add(targetsdk.getExportDir());
                try {
                    verifier.verify(cap, new ArrayList<>(exps));
                    log(String.format("Verification of %s passed", cap), Project.MSG_INFO);
                } catch (VerifierError | IOException e) {
                    throw new BuildException(String.format("Verification of %s failed: %s", cap, e.getMessage()));
                }
            }

            setTaskName("cap");
            // Copy resources to final destination
            try {
                // check that a CAP file got created
                if (!Files.exists(cap)) {
                    throw new BuildException("Can not find CAP in " + jcsrc);
                }

                // copy CAP file
                CAPFile capfile = CAPFile.fromBytes(Files.readAllBytes(cap));

                // Create output name, if not given.
                output_cap = capFileName(capfile, output_cap);

                // resolve output path
                Path outCap = project.resolveFile(output_cap).toPath();

                // strip classes, if asked
                if (strip) {
                    CAPFile.strip(cap);
                }

                // perform the copy
                Files.copy(cap, outCap, StandardCopyOption.REPLACE_EXISTING);
                // report destination
                log("CAP saved to " + outCap, Project.MSG_INFO);

                // copy EXP file
                if (output_exp != null) {
                    setTaskName("exp");
                    // check that an EXP file got created
                    if (!Files.exists(exp)) {
                        throw new BuildException("Can not find EXP in " + jcsrc);
                    }
                    // resolve output directory
                    Path outExp = project.resolveFile(output_exp).toPath();
                    // determine package directories
                    Path outExpPkg = outExp.resolve(pkgPath);
                    Path outExpPkgJc = outExpPkg.resolve("javacard");
                    // create directories
                    if (!Files.exists(outExpPkgJc)) {
                        Files.createDirectories(outExpPkgJc);
                    }
                    // perform the copy
                    Path exp_file = outExpPkgJc.resolve(exp.getFileName());

                    Files.copy(exp, exp_file, StandardCopyOption.REPLACE_EXISTING);
                    // report destination
                    log("EXP saved to " + exp_file, Project.MSG_INFO);
                    // add the export directory to the export path for verification
                    exps.add(outExp);
                }

                // copy JCA file
                if (output_jca != null) {
                    setTaskName("jca");
                    // check that a JCA file got created
                    if (!Files.exists(jca)) {
                        throw new BuildException("Can not find JCA in " + jcsrc);
                    }
                    // resolve output path
                    outCap = project.resolveFile(output_jca).toPath();
                    Files.copy(jca, outCap, StandardCopyOption.REPLACE_EXISTING);
                    log("JCA saved to " + outCap.toAbsolutePath(), Project.MSG_INFO);
                }

                // create JAR file
                if (output_jar != null) {
                    setTaskName("jar");
                    File outJar = project.resolveFile(output_jar);
                    // create a new JAR task
                    Jar jarz = new Jar();
                    jarz.setProject(project);
                    jarz.setTaskName("jar");
                    jarz.setDestFile(outJar);
                    // include class files
                    FileSet jarcls = new FileSet();
                    jarcls.setDir(project.resolveFile(classes_path));
                    jarz.add(jarcls);
                    // include conversion output
                    FileSet jarout = new FileSet();
                    jarout.setDir(applet_folder.toFile());
                    jarz.add(jarout);
                    // create the JAR
                    jarz.execute();
                    log("JAR saved to " + outJar.getAbsolutePath(), Project.MSG_INFO);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new BuildException("Can not copy output CAP, EXP or JCA", e);
            }
        } finally {
            Misc.cleanTemp();
        }
    }

    private String capFileName(CAPFile cap, String template) {
        // Override %n with build-context class name (more reliable than CAP metadata for 2.x)
        String commonNameOverride = null;
        if (cap.getAppletAIDs().size() == 1 && !cap.getFlags().contains("exports")) {
            commonNameOverride = raw_applets.get(0).klass;
        }
        return Misc.capFileName(cap, template, commonNameOverride);
    }
}
