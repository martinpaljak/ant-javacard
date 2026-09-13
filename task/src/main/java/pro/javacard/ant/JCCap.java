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
import pro.javacard.capfile.AID;
import pro.javacard.capfile.CAPFile;
import pro.javacard.capfile.CAPPackage;
import pro.javacard.capfile.ExportFileHelper;
import pro.javacard.capfile.HexUtils;
import pro.javacard.sdk.Converter;
import pro.javacard.sdk.JavaCardSDK;
import pro.javacard.sdk.SDKLogger;
import pro.javacard.sdk.OffCardVerifier;
import pro.javacard.sdk.SDKRelease;
import pro.javacard.sdk.APIVersion;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static pro.javacard.sdk.APIVersion.*;

// <cap ...>...</cap> and actual execution of core task.
public class JCCap extends Task {

    static final String DEFAULT_CAP_NAME_TEMPLATE = "%n_%a_%h_%j_%J.cap";
    static final String DEFAULT_CAP_NAME_TEMPLATE_LIB = "%n_%a_%v_%h_%J.cap";

    private final String master_jckit_path;
    // Folders under the system temp that this <cap> removes when done or interrupted
    private final List<Path> temporary = new ArrayList<>();
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
    private String raw_targetsdk = null;

    private boolean verify = true;
    private boolean debug = false;
    private boolean strip = false;
    private boolean ints = false;
    private boolean exportmap = false;
    // Escape hatch for when in-process conversion misbehaves
    private final boolean fork = Boolean.parseBoolean(System.getenv().getOrDefault("_ANT_JAVACARD_FORK", "false"));

    public JCCap(String master_jckit_path) {
        this.master_jckit_path = master_jckit_path;
    }

    @SuppressWarnings("unused")
    public void setJCKit(String msg) {
        jckit_path = msg;
    }

    @SuppressWarnings("unused")
    public void setOutput(String msg) {
        output_cap = msg;
    }

    @SuppressWarnings("unused")
    public void setExport(String msg) {
        output_exp = msg;
    }

    @SuppressWarnings("unused")
    public void setJar(String msg) {
        output_jar = msg;
    }

    @SuppressWarnings("unused")
    public void setJca(String msg) {
        output_jca = msg;
    }

    @SuppressWarnings("unused")
    public void setPackage(String msg) {
        package_name = msg;
    }

    @SuppressWarnings("unused")
    public void setClasses(String msg) {
        classes_path = msg;
    }

    @SuppressWarnings("unused")
    public void setVersion(String msg) {
        package_version = msg;
    }

    @SuppressWarnings("unused")
    public void setSources(String arg) {
        sources_path = arg;
    }

    @SuppressWarnings("unused")
    public void setSources2(String arg) {
        sources2_path = arg;
    }

    @SuppressWarnings("unused")
    public void setIncludes(String arg) {
        includes = arg;
    }

    @SuppressWarnings("unused")
    public void setExcludes(String arg) {
        excludes = arg;
    }

    @SuppressWarnings("unused")
    public void setVerify(boolean arg) {
        verify = arg;
    }

    @SuppressWarnings("unused")
    public void setDebug(boolean arg) {
        debug = arg;
    }

    @SuppressWarnings("unused")
    public void setStrip(boolean arg) {
        strip = arg;
    }

    @SuppressWarnings("unused")
    public void setInts(boolean arg) {
        ints = arg;
    }

    @SuppressWarnings("unused")
    public void setExportmap(boolean arg) {
        exportmap = arg;
    }

    @SuppressWarnings("unused")
    public void setTargetsdk(String arg) {
        raw_targetsdk = arg;
    }

    @SuppressWarnings("unused")
    public void setAID(String msg) {
        try {
            package_aid = HexUtils.stringToBin(msg);
            if (package_aid.length < 5 || package_aid.length > 16) {
                throw new BuildException(String.format("Package AID must be between 5 and 16 bytes: %s (%d)", HexUtils.bin2hex(package_aid), package_aid.length));
            }

        } catch (IllegalArgumentException e) {
            throw new BuildException("Not a correct package AID: " + e.getMessage());
        }
    }

    // Many applets inside one package
    @SuppressWarnings("unused")
    public JCApplet createApplet() {
        JCApplet applet = new JCApplet();
        raw_applets.add(applet);
        return applet;
    }

    // Many imports inside one package
    @SuppressWarnings("unused")
    public JCImport createImport() {
        JCImport imp = new JCImport();
        raw_imports.add(imp);
        return imp;
    }

    // Gradle reserves the name "import"
    @SuppressWarnings("unused")
    public JCImport createJimport() {
        return this.createImport();
    }

    // Nested <sources path="" includes="" excludes=""/> elements
    @SuppressWarnings("unused")
    public JCSources createSources() {
        JCSources src = new JCSources();
        raw_sources.add(src);
        return src;
    }

    // A folder holding an SDK newer than the version table is the user's to fix
    private static Optional<JavaCardSDK> detectSDK(Path path) {
        try {
            return JavaCardSDK.detectSDK(path);
        } catch (IllegalArgumentException e) {
            throw new HelpingBuildException(e.getMessage(), HelpingBuildException.COMPATIBILITY);
        }
    }

    // Names the location that was tried when it holds no SDK
    private Optional<JavaCardSDK> resolveSDK(String location, String source) {
        Path path = getProject().resolveFile(location).toPath();
        Optional<JavaCardSDK> sdk = detectSDK(path);
        if (!sdk.isPresent()) {
            log(String.format("No JavaCard SDK in %s (from %s)", path, source), Project.MSG_WARN);
        }
        return sdk;
    }

    // The first configured location wins
    private Optional<JavaCardSDK> findSDK() {
        // See issue #2
        String propPath = getProject().getProperty("jc.home");
        if (propPath != null) {
            return resolveSDK(propPath, "\"jc.home\" property");
        }
        if (jckit_path != null) {
            return resolveSDK(jckit_path, "\"jckit\" attribute of <cap>");
        }
        if (master_jckit_path != null) {
            return resolveSDK(master_jckit_path, "\"jckit\" attribute of <javacard>");
        }
        String envPath = System.getenv("JC_HOME");
        if (envPath != null) {
            return resolveSDK(envPath, "JC_HOME environment variable");
        }
        return Optional.empty();
    }

    // Turns what the setters recorded into the plan
    Build resolve() {
        Build build = new Build();
        build.sdk = findSDK().orElseThrow(() -> new HelpingBuildException("No usable JavaCard SDK: set the \"jckit\" attribute, the \"jc.home\" property or $JC_HOME"));
        SDKRelease release = build.sdk.getRelease();
        int jdk = Misc.getCurrentJDKVersion();

        log("INFO: using JavaCard " + build.sdk.api().version() + " SDK in " + build.sdk.getRoot() + " with JDK " + jdk, Project.MSG_INFO);

        // Refuse before copying sources or making an output folder
        // See https://github.com/martinpaljak/ant-javacard/issues/79
        if (jdk < release.oldestJDK() || jdk > release.newestJDK()) {
            // Only LTS releases get advertised
            String lts = JavaCard.LTS.stream().filter(v -> v >= release.oldestJDK() && v <= release.newestJDK())
                    .map(Object::toString).collect(Collectors.joining(", "));
            throw new HelpingBuildException(String.format("JavaCard SDK v%s (JavaCard %s) runs on JDK %s, not on JDK %d",
                    release, build.sdk.api().version(), lts, jdk), HelpingBuildException.COMPATIBILITY);
        }
        build.javacTarget = release.javacTarget(jdk);

        if (raw_targetsdk != null) {
            Optional<APIVersion> named = APIVersion.fromVersion(raw_targetsdk);
            if (named.isPresent()) {
                build.target = build.sdk.api(named.get()).orElseThrow(() -> new HelpingBuildException(
                        String.format("JavaCard SDK v%s can not build against JavaCard %s, only %s",
                                release, named.get(), build.sdk.provides().stream().map(Object::toString).collect(Collectors.joining(", "))),
                        HelpingBuildException.COMPATIBILITY));
            } else {
                JavaCardSDK other = detectSDK(getProject().resolveFile(raw_targetsdk).toPath())
                        .orElseThrow(() -> new HelpingBuildException("Invalid \"targetsdk\": " + raw_targetsdk));
                build.target = other.api();
                // A multi-target SDK carries no export files below the oldest version it names
                Optional<APIVersion> oldest = release.oldestTarget();
                if (oldest.isPresent() && !build.target.version().equalOrNewer(oldest.get())) {
                    throw new HelpingBuildException(String.format("targetsdk %s is not compatible with JavaCard SDK v%s", build.target.version(), release));
                }
                // A multi-target converter reads the export files of the named version from its own tools.jar
                // TODO: turn this into an error at some point
                if (release.targets().contains(build.target.version())) {
                    log(String.format("WARN: v%s SDK uses its own JavaCard %s export files, not %s",
                            release, build.target.version(), raw_targetsdk), Project.MSG_WARN);
                }
            }
            if (build.target.sdk().getRoot().equals(build.sdk.getRoot())) {
                log("INFO: targeting JavaCard " + build.target.version(), Project.MSG_INFO);
            } else {
                log(String.format("INFO: targeting JavaCard %s SDK in %s", build.target.version(), build.target.sdk().getRoot()), Project.MSG_INFO);
            }
        } else {
            build.target = build.sdk.api();
        }

        // Nudge towards the latest SDK in the appropriate family
        SDKRelease recommended = SDKRelease.V26_0.targets().contains(build.target.version()) ? SDKRelease.V26_0 : SDKRelease.V305u4;
        if (release != recommended) {
            String sdkHint = recommended == SDKRelease.V26_0 ? "jc320v26.0_kit" : "jc305u4_kit";
            log(String.format("WARN: using JavaCard SDK v%s instead of the recommended v%s (%s)", release, recommended, sdkHint), Project.MSG_WARN);
        }

        build.verify = verify;
        if (build.verify && build.target.version().isOneOf(V211, V212)) {
            log("WARN: verification not supported for JavaCard " + build.target.version(), Project.MSG_WARN);
            build.verify = false;
        }

        // Warn about deprecation in future
        if (sources_path != null && sources2_path != null) {
            log("WARN: sources2 is deprecated in favor of multiple paths in sources", Project.MSG_WARN);
        }

        if (!raw_sources.isEmpty() && (sources_path != null || sources2_path != null || includes != null || excludes != null)) {
            throw new HelpingBuildException("Nested <sources> elements can not be combined with sources, sources2, includes or excludes attributes");
        }

        String sources = sources_path;
        // Shorthand for simple small projects - use Maven conventions
        if (sources == null && classes_path == null && raw_sources.isEmpty()) {
            if (getProject().resolveFile("src/main/javacard").isDirectory()) {
                sources = "src/main/javacard";
            } else if (getProject().resolveFile("src/main/java").isDirectory()) {
                sources = "src/main/java";
            }
        }

        // sources or classes must be set
        if (sources == null && classes_path == null && raw_sources.isEmpty()) {
            throw new HelpingBuildException("Must specify \"sources\" or \"classes\"");
        }

        if (sources != null) {
            for (String path : sources.split(Pattern.quote(File.pathSeparator))) {
                build.sources.add(getProject().resolveFile(path).toPath());
            }
        }
        // Old style - second folder
        if (sources2_path != null) {
            build.sources.add(getProject().resolveFile(sources2_path).toPath());
        }
        build.includes = includes;
        build.excludes = excludes;

        // Check nested sources
        for (JCSources s : raw_sources) {
            if (s.path == null) {
                throw new HelpingBuildException("Nested <sources> element must have a \"path\" attribute");
            }
            if (!getProject().resolveFile(s.path).isDirectory()) {
                throw new HelpingBuildException("Sources path does not exist: " + s.path);
            }
            build.nested.add(s);
        }

        if (classes_path != null) {
            build.classes = getProject().resolveFile(classes_path).toPath();
        }

        // Check package version
        String version = package_version == null ? "0.0" : package_version;
        // u1 in the CAP and signed bytes in the .jca
        if (!version.matches("^[0-9]{1,3}\\.[0-9]{1,3}$")) {
            throw new HelpingBuildException("Invalid package version: " + version);
        }
        if (Arrays.stream(version.split("\\.")).map(e -> Integer.parseInt(e, 10)).anyMatch(e -> (e < 0 || e > 127))) {
            throw new HelpingBuildException("Illegal package version value (0..127): " + version);
        }

        // The "exps" folder of an <import> wins over its JAR
        for (JCImport a : raw_imports) {
            if (a.exps == null && a.jar == null) {
                throw new HelpingBuildException("An <import> must set \"exps\", \"jar\" or both");
            }
            if (a.jar != null) {
                File jar = getProject().resolveFile(a.jar);
                if (!jar.isFile()) {
                    throw new HelpingBuildException("Import JAR does not exist: " + a.jar);
                }
                build.importJars.add(jar.toPath());
            }
            if (a.exps != null && !getProject().resolveFile(a.exps).isDirectory()) {
                throw new HelpingBuildException("Import EXP files folder does not exist: " + a.exps);
            }
            Path exports = getProject().resolveFile(a.exps != null ? a.exps : a.jar).toPath();
            if (!build.importExports.contains(exports)) {
                build.importExports.add(exports);
            }
        }

        // Construct applets and fill in missing bits from package info
        String name = package_name;
        byte[] aid = package_aid;
        int applet_counter = 0;
        for (JCApplet a : raw_applets) {
            // Keep count for automagic numbering
            applet_counter = applet_counter + 1;

            if (a.klass == null) {
                throw new HelpingBuildException("Applet class is missing");
            }
            String klass = a.klass;
            // A package name must match the applet
            if (name != null) {
                if (!klass.contains(".")) {
                    klass = String.format("%s.%s", name, klass);
                } else if (!klass.startsWith(name)) {
                    throw new HelpingBuildException(String.format("Applet class %s is not in package %s", klass, name));
                }
            } else {
                if (klass.contains(".")) {
                    name = klass.substring(0, klass.lastIndexOf("."));
                    log("INFO: setting package name to " + name, Project.MSG_INFO);
                } else {
                    throw new HelpingBuildException("Applet must be in a package!");
                }
            }

            byte[] applet_aid = a.aid;
            if (aid != null) {
                if (applet_aid != null) {
                    // RID-s must match
                    if (!Arrays.equals(Arrays.copyOf(aid, 5), Arrays.copyOf(applet_aid, 5))) {
                        throw new HelpingBuildException("Package RID does not match Applet RID");
                    }
                } else {
                    if (aid.length == 16) {
                        throw new HelpingBuildException("Can not add a number to a 16 byte package AID: set the \"aid\" attribute of applet " + klass);
                    }
                    // make "magic" applet AID from package_aid + counter
                    applet_aid = Arrays.copyOf(aid, aid.length + 1);
                    applet_aid[aid.length] = (byte) applet_counter;
                    log("INFO: generated applet AID: " + HexUtils.bin2hex(applet_aid) + " for " + klass, Project.MSG_INFO);
                }
            } else {
                // A missing package AID takes the RID of the applet AID
                if (applet_aid != null) {
                    aid = Arrays.copyOf(applet_aid, 5);
                } else {
                    throw new HelpingBuildException("Both package AID and applet AID are missing!");
                }
            }
            AID id = new AID(applet_aid);
            if (build.applets.put(id, klass) != null) {
                throw new HelpingBuildException("Two applets with the same AID: " + id);
            }
        }

        // Check package AID
        if (aid == null) {
            throw new HelpingBuildException("Must specify package AID");
        }

        // Package name must be present if no applets
        if (build.applets.isEmpty()) {
            if (name == null) {
                throw new HelpingBuildException("Must specify package name if no applets");
            }
            log(String.format("Building library from package %s (AID: %s)", name, HexUtils.bin2hex(aid)), Project.MSG_INFO);
        } else {
            log(String.format("Building CAP with %d applet%s from package %s (AID: %s)", applet_counter, applet_counter > 1 ? "s" : "", name, HexUtils.bin2hex(aid)), Project.MSG_INFO);
            for (Map.Entry<AID, String> app : build.applets.entrySet()) {
                log(String.format("%s %s", app.getValue(), app.getKey()), Project.MSG_INFO);
            }
        }

        String[] numbers = version.split("\\.");
        build.pkg = new CAPPackage(new AID(aid), Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]), name);

        if (output_exp != null) {
            build.exp = getProject().resolveFile(output_exp).toPath();
        }
        if (output_jca != null) {
            build.jca = getProject().resolveFile(output_jca).toPath();
        }
        if (output_jar != null) {
            build.jar = getProject().resolveFile(output_jar).toPath();
        } else if (build.exp != null) {
            build.jar = build.exp.resolve(build.name() + ".jar");
        }

        // A folder output gets the default CAP file name
        String template = build.applets.isEmpty() ? DEFAULT_CAP_NAME_TEMPLATE_LIB : DEFAULT_CAP_NAME_TEMPLATE;
        File cap = getProject().resolveFile(output_cap == null ? template : output_cap);
        // A trailing separator names a folder that does not have to exist yet
        boolean folder = output_cap != null && (cap.isDirectory() || output_cap.endsWith("/") || output_cap.endsWith(File.separator));
        build.cap = (folder ? Paths.get(cap.toString(), template) : cap.toPath()).toString();

        build.debug = debug;
        build.strip = strip;
        build.ints = ints;
        build.exportmap = exportmap;
        return build;
    }

    // To lessen the java.nio and apache.ant namespace clash...
    private org.apache.tools.ant.types.Path mkPath(String name) {
        if (name == null) {
            return new org.apache.tools.ant.types.Path(getProject());
        }
        return new org.apache.tools.ant.types.Path(getProject(), name);
    }

    private void compile(Build build, Path classes) {
        Project project = getProject();
        setTaskName("compile");

        // construct javac task
        Javac j = new Javac();
        j.setProject(project);
        // See https://github.com/martinpaljak/ant-javacard/pull/96
        j.setEncoding("utf-8");
        j.setTaskName("compile");

        org.apache.tools.ant.types.Path sources = mkPath(null);

        if (!build.nested.isEmpty()) {
            // Per-path includes/excludes via nested <sources> elements
            setTaskName("sources");
            Path mergedDir = temp("sources");
            for (JCSources src : build.nested) {
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
            for (Path path : build.sources) {
                sources.append(mkPath(path.toString()));
            }

            if (build.includes != null) {
                j.setIncludes(build.includes);
            }

            if (build.excludes != null) {
                j.setExcludes(build.excludes);
            }
        }
        j.setSrcdir(sources);

        // The sources/includes/excludes parameters already resolve the files to compile
        j.setSourcepath(new org.apache.tools.ant.types.Path(project, null));

        log("Compiling files from " + sources, Project.MSG_INFO);

        if (!Files.exists(classes)) {
            try {
                Files.createDirectories(classes);
            } catch (IOException e) {
                throw new BuildException("Could not create classes folder " + classes.toAbsolutePath());
            }
        }

        j.setDestdir(classes.toFile());
        // See "Setting Java Compiler Options" in User Guide
        j.setDebug(true);
        j.setDebugLevel("lines,vars,source");

        j.setTarget(build.javacTarget);
        j.setSource(build.javacTarget);

        j.setIncludeantruntime(false);
        j.createCompilerArg().setValue("-Xlint");
        j.createCompilerArg().setValue("-Xlint:-options");
        j.createCompilerArg().setValue("-Xlint:-serial");
        if (SDKRelease.STRING_PROCESSOR.contains(build.sdk.getRelease())) {
            j.createCompilerArg().setLine("-processor com.oracle.javacard.stringproc.StringConstantsProcessor");
            org.apache.tools.ant.types.Path pcp = new Javac().createClasspath();
            for (Path jar : build.sdk.getCompilerJars()) {
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
        for (Path jar : build.target.apiJars()) {
            cp.append(mkPath(jar.toString()));
        }
        for (Path jar : build.importJars) {
            cp.append(mkPath(jar.toString()));
        }
        j.execute();
    }

    // Puts every imported export file into the one tree the conversion uses as export path
    private Path exportTree(Build build) {
        Path root = temp("exports");
        // package name -> the export file that defined it
        Map<String, String> supplied = new HashMap<>();
        int jars = 0;
        for (Path source : build.importExports) {
            try {
                if (Files.isDirectory(source)) {
                    for (Path exp : OffCardVerifier.expFiles(source)) {
                        place(root, exp, exp.toString(), supplied);
                    }
                } else {
                    Path folder = temp("imports-" + jars++);
                    OffCardVerifier.extractExps(source, folder);
                    for (Path exp : OffCardVerifier.expFiles(folder)) {
                        place(root, exp, source + "!" + folder.relativize(exp), supplied);
                    }
                }
            } catch (IOException e) {
                throw new BuildException("Can not read export files from " + source, e);
            }
        }
        return root;
    }

    // Where the converter keeps the artifacts of a package
    private static Path jcDir(Path root, String pkg) {
        return root.resolve(pkg.replace(".", File.separator)).resolve("javacard");
    }

    // Copies an export file where the converter looks for its package
    private void place(Path root, Path exp, String origin, Map<String, String> supplied) {
        final ExportFileHelper.PackageInfo pkg;
        try {
            pkg = ExportFileHelper.parsePackage(exp);
        } catch (IOException | IllegalArgumentException e) {
            throw new BuildException(String.format("Not a usable export file: %s (%s)", origin, e.getMessage()));
        }
        String from = String.format("%s (%s)", origin, pkg);
        try {
            Path folder = jcDir(root, pkg.getName());
            Path target = folder.resolve(Misc.lastName(pkg.getName()) + ".exp");
            String previous = supplied.get(pkg.getName());
            if (previous != null) {
                if (!Arrays.equals(Files.readAllBytes(exp), Files.readAllBytes(target))) {
                    throw new HelpingBuildException(String.format("Two sources for package %s: %s and %s", pkg.getName(), previous, from));
                }
                log(String.format("WARN: package %s supplied twice, identical: %s and %s", pkg.getName(), previous, from), Project.MSG_WARN);
                return;
            }
            supplied.put(pkg.getName(), from);
            Files.createDirectories(folder);
            Files.copy(exp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BuildException("Can not place export file: " + origin, e);
        }
    }

    private void convert(Build build, Path classes, Path exports, Path output) {
        setTaskName("convert");

        Converter converter = Converter.of(build.sdk, build.target)
                .into(output)
                .classes(classes)
                .exports(exports)
                .applets(build.applets)
                .debug(build.debug)
                .verify(build.verify)
                .ints(build.ints)
                .exportmap(build.exportmap)
                // Nothing can import a JAR without the export file of its package
                .exp(build.exp != null || build.jar != null)
                .jca(build.jca != null);

        List<String> args = converter.arguments(build.pkg);

        // Only the v26.0 tools are free of System.exit calls
        if (!fork && SDKLogger.loggingConfig().isPresent() && build.sdk.getRelease() == SDKRelease.V26_0) {
            convertInProcess(converter, args);
        } else {
            convertForked(build, converter, args);
        }

        // XXX: SDK v25.0 exits without error on conversion issues
        if (!Files.exists(artifact(build, output, ".cap"))) {
            throw new BuildException("CAP file not generated, check conversion for errors!");
        }
    }

    private void convertForked(Build build, Converter converter, List<String> args) {
        Java j = new Java(this);
        j.setTaskName("convert");
        j.setFailonerror(true);
        j.setFork(true);

        // classpath to jckit bits
        org.apache.tools.ant.types.Path cp = j.createClasspath();
        for (Path jar : build.sdk.getToolJars()) {
            cp.append(mkPath(jar.toString()));
        }
        j.setClasspath(cp);
        j.setClassname(converter.mainClass());

        // Don't create java0.log.0 files in home folder
        if (SDKRelease.LOGS_TO_FILE.contains(build.sdk.getRelease())) {
            SDKLogger.loggingConfig().ifPresent(logconf -> {
                Environment.Variable jclog = new Environment.Variable();
                jclog.setKey("java.util.logging.config.file");
                jclog.setValue(logconf);
                j.addSysproperty(jclog);
            });
        }

        for (String arg : args) {
            j.createArg().setValue(arg);
        }

        log("command: " + j.getCommandLine(), Project.MSG_DEBUG);

        j.execute();
    }

    private void convertInProcess(Converter converter, List<String> args) {
        log("command: " + String.join(" ", args), Project.MSG_DEBUG);
        Misc.AntLog handler = new Misc.AntLog(this);
        try {
            converter.convert(args, handler);
        } catch (InvocationTargetException e) {
            throw new BuildException("Converter failed: " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (ReflectiveOperationException | IOException e) {
            throw new BuildException("Could not run the converter: " + e.getMessage(), e);
        }
        // The converter swallows every throwable and only logs the failure
        if (handler.failed) {
            throw new BuildException("Conversion failed with errors logged above");
        }
    }

    // What the converter wrote for the package
    private static Path artifact(Build build, Path output, String extension) {
        return jcDir(output, build.packageName()).resolve(build.name() + extension);
    }

    private void deliver(Build build, Path classes, Path output) {
        Project project = getProject();
        setTaskName("cap");
        Path cap = artifact(build, output, ".cap");
        try {
            CAPFile capfile = CAPFile.fromBytes(Files.readAllBytes(cap));

            if (build.strip) {
                CAPFile.strip(cap);
            }

            // Only the build knows the applet classes that applet.xml needs
            CAPFile.amendMetadata(cap, build.applets);

            copy("CAP", cap, Paths.get(Misc.capFileName(capfile, build.cap, build.firstApplet())));

            if (build.exp != null) {
                setTaskName("exp");
                copy("EXP", artifact(build, output, ".exp"), jcDir(build.exp, build.packageName()).resolve(build.name() + ".exp"));
            }

            if (build.jca != null) {
                setTaskName("jca");
                copy("JCA", artifact(build, output, ".jca"), build.jca);
            }

            // create JAR file
            if (build.jar != null) {
                setTaskName("jar");
                // create a new JAR task
                Jar jarz = new Jar();
                jarz.setProject(project);
                jarz.setTaskName("jar");
                jarz.setDestFile(build.jar.toFile());
                // include class files
                FileSet jarcls = new FileSet();
                jarcls.setDir(classes.toFile());
                jarz.add(jarcls);
                // include conversion output
                FileSet jarout = new FileSet();
                jarout.setDir(output.toFile());
                jarz.add(jarout);
                // create the JAR
                jarz.execute();
                log("JAR saved to " + build.jar, Project.MSG_INFO);
            }
        } catch (IOException e) {
            throw new BuildException(String.format("Can not write the output files: %s (%s)", e.getMessage(), e.getClass().getSimpleName()), e);
        }
    }

    private void copy(String kind, Path from, Path to) throws IOException {
        if (!Files.exists(from)) {
            throw new BuildException(String.format("Can not find %s in %s", kind, from.getParent()));
        }
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        log(String.format("%s saved to %s", kind, to), Project.MSG_INFO);
    }

    // Scopes a subfolder of $ANT_JAVACARD_TMP to this <cap/>
    private Path temp(String kind) {
        return Misc.makeTemp(kind + "-" + Objects.hashCode(this), temporary);
    }

    @Override
    public void execute() {
        setTaskName("javacard");

        Build build = resolve();

        try {
            Path classes = build.classes == null ? temp("classes") : build.classes;
            if (build.compiles()) {
                compile(build, classes);
            }
            Path exports = build.importExports.isEmpty() ? null : exportTree(build);
            Path output = temp("applet");
            convert(build, classes, exports, output);
            deliver(build, classes, output);
        } finally {
            cleanTemp();
        }
    }

    void cleanTemp() {
        Misc.cleanTemp(temporary);
    }
}
