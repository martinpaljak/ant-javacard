/**
 * Copyright (c) 2015-2018 Martin Paljak
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pro.javacard.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Environment.Variable;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import pro.javacard.CAPFile;
import pro.javacard.JavaCardSDK;
import pro.javacard.OffCardVerifier;
import pro.javacard.VerifierError;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static pro.javacard.JavaCardSDK.Version.V211;
import static pro.javacard.JavaCardSDK.Version.V212;
import static pro.javacard.JavaCardSDK.Version.V304;
import static pro.javacard.JavaCardSDK.Version.V305;
import static pro.javacard.JavaCardSDK.Version.V310;

public final class JavaCard extends Task {
    private List<File> temporary = new ArrayList<>();
    // This code has been taken from Apache commons-codec 1.7 (License: Apache
    // 2.0)
    private static final char[] LOWER_HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private String master_jckit_path = null;
    private Vector<JCCap> packages = new Vector<>();

    private static String hexAID(byte[] aid) {
        StringBuffer hexaid = new StringBuffer();
        for (byte b : aid) {
            hexaid.append(String.format("0x%02X", b));
            hexaid.append(":");
        }
        String hex = hexaid.toString();
        // Cut off the final colon
        return hex.substring(0, hex.length() - 1);
    }

    // For cleaning up temporary files
    private static void rmminusrf(java.nio.file.Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<java.nio.file.Path>() {
                @Override
                public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException e)
                        throws IOException {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (FileNotFoundException | NoSuchFileException e) {
            // Already gone - do nothing.
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encodeHexString(final byte[] data) {
        final int l = data.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = LOWER_HEX[(0xF0 & data[i]) >>> 4];
            out[j++] = LOWER_HEX[0x0F & data[i]];
        }
        return new String(out);
    }

    public static byte[] decodeHexString(String str) {
        char data[] = str.toCharArray();
        final int len = data.length;
        if ((len & 0x01) != 0) {
            throw new IllegalArgumentException("Odd number of characters: " + str);
        }
        final byte[] out = new byte[len >> 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; j < len; i++) {
            int f = Character.digit(data[j], 16) << 4;
            j++;
            f = f | Character.digit(data[j], 16);
            j++;
            out[i] = (byte) (f & 0xFF);
        }
        return out;
    }

    public static byte[] stringToBin(String s) {
        s = s.toLowerCase().replaceAll(" ", "").replaceAll(":", "");
        s = s.replaceAll("0x", "").replaceAll("\n", "").replaceAll("\t", "");
        s = s.replaceAll(";", "");
        return decodeHexString(s);
    }

    public void setJCKit(String msg) {
        master_jckit_path = msg;
    }

    public JCCap createCap() {
        JCCap pkg = new JCCap();
        packages.add(pkg);
        return pkg;
    }

    @Override
    public void execute() {
        Thread cleanup = new Thread(() -> {
            log("Ctrl-C, cleaning up", Project.MSG_INFO);
            cleanTemp();
        });
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            for (JCCap p : packages) {
                p.execute();
            }
        } finally {
            Runtime.getRuntime().removeShutdownHook(cleanup);
        }
    }

    private void cleanTemp() {
        // Clean temporary files.
        for (File f : temporary) {
            if (f.exists()) {
                rmminusrf(f.toPath());
            }
        }
    }

    public static class JCApplet {
        private String klass = null;
        private byte[] aid = null;

        public JCApplet() {
        }

        public void setClass(String msg) {
            klass = msg;
        }

        public void setAID(String msg) {
            try {
                aid = stringToBin(msg);
                if (aid.length < 5 || aid.length > 16) {
                    throw new BuildException("Applet AID must be between 5 and 16 bytes: " + aid.length);
                }
            } catch (IllegalArgumentException e) {
                throw new BuildException("Not a valid applet AID: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("serial")
    public static class HelpingBuildException extends BuildException {
        public HelpingBuildException(String msg) {
            super(msg + "\n\nPLEASE READ https://github.com/martinpaljak/ant-javacard#syntax");
        }
    }

    public class JCCap extends Task {
        private JavaCardSDK jckit = null;
        private String classes_path = null;
        private String sources_path = null;
        private String sources2_path = null;
        private String includes = null;
        private String excludes = null;
        private String package_name = null;
        private byte[] package_aid = null;
        private String package_version = null;
        private Vector<JCApplet> raw_applets = new Vector<>();
        private Vector<JCImport> raw_imports = new Vector<>();
        private String output_cap = null;
        private String output_exp = null;
        private String output_jar = null;
        private String output_jca = null;
        private String jckit_path = null;
        private JavaCardSDK targetsdk = null;
        private String java_version = null;
        private boolean verify = true;
        private boolean debug = false;
        private boolean ints = false;


        public JCCap() {
        }

        public void setJCKit(String msg) {
            jckit_path = msg;
        }

        public void setJavaversion(String msg) {
            java_version = msg;
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

        public void setInts(boolean arg) {
            ints = arg;
        }

        public void setTargetsdk(String arg) {
            targetsdk = JavaCardSDK.detectSDK(getProject().resolveFile(arg).getAbsolutePath());
            if (targetsdk == null) {
                throw new BuildException("Invalid targetsdk: " + arg);
            }
        }

        public void setAID(String msg) {
            if (package_aid != null) {
                throw new BuildException("Package AID already created from fidesmoappid");
            }
            try {
                package_aid = stringToBin(msg);
                if (package_aid.length < 5 || package_aid.length > 16)
                    throw new BuildException("Package AID must be between 5 and 16 bytes: " + encodeHexString(package_aid) + " (" + package_aid.length + ")");

            } catch (IllegalArgumentException e) {
                throw new BuildException("Not a correct package AID: " + e.getMessage());
            }
        }

        public void setFidesmoAppId(String appid) {
            if (package_aid != null) {
                throw new BuildException("Can't have aid and fidesmoappid at the same time!");
            }
            try {
                byte[] id = stringToBin(appid);
                if (id.length == 4) {
                    // TODO: reference to docs
                    package_aid = stringToBin("A0000006170011223344");
                    System.arraycopy(id, 0, package_aid, 6, id.length);
                } else {
                    throw new BuildException("Fidesmo appId must be 4 bytes: " + encodeHexString(id) + " (" + id.length + ")");
                }
            } catch (IllegalArgumentException e) {
                throw new BuildException("Not a correct Fidesmo appId: " + e.getMessage());
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

        private JavaCardSDK findSDK() {
            // try configuration first
            if (jckit_path != null) {
                return JavaCardSDK.detectSDK(getProject().resolveFile(jckit_path).getAbsolutePath());
            }
            if (master_jckit_path != null) {
                return JavaCardSDK.detectSDK(getProject().resolveFile(master_jckit_path).getAbsolutePath());
            }
            // now check via ant property
            String propPath = getProject().getProperty("jc.home");
            if (propPath != null) {
                return JavaCardSDK.detectSDK(getProject().resolveFile(propPath).getAbsolutePath());
            }
            // finally via the environment
            String envPath = System.getenv("JC_HOME");
            if (envPath != null) {
                return JavaCardSDK.detectSDK(getProject().resolveFile(envPath).getAbsolutePath());
            }
            // return null if no options
            return null;
        }

        // Check that arguments are sufficient and do some DWIM
        private void check() {
            jckit = findSDK();

            // Sanity check
            if (jckit == null) {
                throw new HelpingBuildException("No usable JavaCard SDK referenced");
            }

            log("INFO: using JavaCard " + jckit.getVersion() + " SDK in " + jckit.getRoot(), Project.MSG_INFO);
            if (targetsdk == null) {
                targetsdk = jckit;
            } else {
                log("INFO: targeting JavaCard " + targetsdk.getVersion() + " SDK in " + targetsdk.getRoot(), Project.MSG_INFO);
            }

            // Shorthand for simple small projects - take javacard sources from src/main/javacard
            if (getProject().resolveFile("src/main/javacard").isDirectory() && sources_path == null && classes_path == null) {
                sources_path = "src/main/javacard";
            }

            // sources or classes must be set
            if (sources_path == null && classes_path == null) {
                throw new HelpingBuildException("Must specify sources or classes");
            }

            // Check package version
            if (package_version == null) {
                package_version = "0.0";
            } else {
                if (!package_version.matches("^[0-9].[0-9]$")) {
                    throw new HelpingBuildException("Incorrect package version: " + package_version);
                }
            }

            // Check imports
            for (JCImport a : raw_imports) {
                if (a.jar != null && !getProject().resolveFile(a.jar).isFile())
                    throw new BuildException("Import JAR does not exist: " + a.jar);
                if (a.exps != null && !getProject().resolveFile(a.exps).isDirectory())
                    throw new BuildException("Import EXP files folder does not exist: " + a.exps);
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
                        a.klass = package_name + "." + a.klass;
                    } else if (!a.klass.startsWith(package_name)) {
                        throw new HelpingBuildException("Applet class " + a.klass + " is not in package " + package_name);
                    }
                } else {
                    if (a.klass.contains(".")) {
                        String pkgname = a.klass.substring(0, a.klass.lastIndexOf("."));
                        log("INFO: Setting package name to " + pkgname, Project.MSG_INFO);
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
                        log("INFO: generated applet AID: " + encodeHexString(a.aid) + " for " + a.klass, Project.MSG_INFO);
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
            if (raw_applets.size() == 0) {
                if (package_name == null)
                    throw new HelpingBuildException("Must specify package name if no applets");
                log("Building library from package " + package_name + " (AID: " + encodeHexString(package_aid) + ")", Project.MSG_INFO);
            } else {
                log("Building CAP with " + applet_counter + " applet" + (applet_counter > 1 ? "s" : "") + " from package " + package_name + " (AID: " + encodeHexString(package_aid) + ")", Project.MSG_INFO);
                for (JCApplet app : raw_applets) {
                    log(app.klass + " " + encodeHexString(app.aid), Project.MSG_INFO);
                }
            }
            if (output_exp != null) {
                // Last component of the package
                String ln = package_name;
                if (ln.lastIndexOf(".") != -1) {
                    ln = ln.substring(ln.lastIndexOf(".") + 1);
                }
                output_jar = new File(output_exp, ln + ".jar").toString();
            }
            // Default output name
            if (output_cap == null) {
                output_cap = "%n_%a_%h_%j.cap"; // SomeApplet_010203040506_9a037e30_2.2.2.cap
            }
        }

        private void compile() {
            Project project = getProject();
            setTaskName("compile");

            // construct javac task
            Javac j = new Javac();
            j.setProject(project);
            j.setTaskName("compile");

            Path sources = new Path(project);
            sources.append(new Path(project, sources_path));
            if (sources2_path != null)
                sources.append(new Path(project, sources2_path));
            j.setSrcdir(sources);

            if (includes != null)
                j.setIncludes(includes);
            if (excludes != null)
                j.setExcludes(excludes);

            log("Compiling files from " + sources, Project.MSG_INFO);

            // determine output directory
            File tmp;
            if (classes_path != null) {
                // if specified use that
                tmp = project.resolveFile(classes_path);
                if (!tmp.exists()) {
                    if (!tmp.mkdir())
                        throw new BuildException("Could not create classes folder " + tmp.getAbsolutePath());
                }
            } else {
                // else generate temporary folder
                tmp = makeTemp();
                classes_path = tmp.getAbsolutePath();
            }

            j.setDestdir(tmp);
            // See "Setting Java Compiler Options" in User Guide
            j.setDebug(true);
            String javaVersion = jckit.getJavaVersion();
            if (java_version != null) {
                javaVersion = java_version;
            }

            j.setTarget(javaVersion);
            j.setSource(javaVersion);
            if (jckit.getVersion().isOneOf(V211, V212)) {
                // Always set debug to disable "contains local variables,
                // but not local variable table." messages
                j.setDebug(true);
            }
            j.setIncludeantruntime(false);
            j.createCompilerArg().setValue("-Xlint");
            j.createCompilerArg().setValue("-Xlint:-options");
            j.createCompilerArg().setValue("-Xlint:-serial");
            if (jckit.getVersion() == JavaCardSDK.Version.V304) {
                //-processor com.oracle.javacard.stringproc.StringConstantsProcessor \
                //                -processorpath "JCDK_HOME/lib/tools.jar;JCDK_HOME/lib/api_classic_annotations.jar" \
                j.createCompilerArg().setLine("-processor com.oracle.javacard.stringproc.StringConstantsProcessor");
                Path pcp = new Javac().createClasspath();
                for (File jar : jckit.getCompilerJars()) {
                    pcp.append(new Path(project, jar.getPath()));
                }
                j.createCompilerArg().setLine("-processorpath \"" + pcp.toString() + "\"");
                j.createCompilerArg().setValue("-Xlint:all,-processing");
            }

            j.setFailonerror(true);
            j.setFork(true);

            // set classpath
            Path cp = j.createClasspath();
            JavaCardSDK sdk = targetsdk == null ? jckit : targetsdk;
            for (File jar : sdk.getApiJars()) {
                cp.append(new Path(project, jar.getPath()));
            }
            for (JCImport i : raw_imports) {
                // Support import clauses with only jar or exp values
                if (i.jar != null) {
                    cp.append(new Path(project, i.jar));
                }
            }
            j.execute();
        }

        private void addKitClasses(Java j) {
            Project project = getProject();
            // classpath to jckit bits
            Path cp = j.createClasspath();
            for (File jar : jckit.getToolJars()) {
                cp.append(new Path(project, jar.getPath()));
            }
            j.setClasspath(cp);
        }

        private File getTargetSdkExportDir() {
            if (jckit.getVersion() == V310) {
                switch (targetsdk.getVersion()) {
                case V310:
                    return targetsdk.getExportDir();
                case V305:
                    return new File(jckit.getRoot().toString(), "api_export_files_3.0.5");
                case V304:
                    return new File(jckit.getRoot().toString(), "api_export_files_3.0.4");
                default:
                    throw new HelpingBuildException("targetsdk incompatible with jckit");

                }
            }
            return targetsdk.getExportDir();
        }

        private void convert(File applet_folder, List<File> exps) {
            setTaskName("convert");
            // construct java task
            Java j = new Java(this);
            j.setTaskName("convert");
            j.setFailonerror(true);
            j.setFork(true);

            // add classpath for SDK tools
            addKitClasses(j);

            // set class depending on SDK
            if (jckit.getVersion().isV3()) {
                j.setClassname("com.sun.javacard.converter.Main");
                // XXX: See https://community.oracle.com/message/10452555
                Variable jchome = new Variable();
                jchome.setKey("jc.home");
                jchome.setValue(jckit.getRoot().toString());
                j.addSysproperty(jchome);
            } else {
                j.setClassname("com.sun.javacard.converter.Converter");
            }

            // output path
            j.createArg().setLine("-d '" + applet_folder.getAbsolutePath() + "'");

            // classes for conversion
            j.createArg().setLine("-classdir '" + classes_path + "'");

            // construct export path
            StringJoiner expstringbuilder = new StringJoiner(File.pathSeparator);

            // Add targetSDK export files
            if (jckit.getVersion() == V310) {
                switch (targetsdk.getVersion()) {
                case V310:
                    j.createArg().setLine("-target 3.1.0");
                    break;
                case V305:
                    j.createArg().setLine("-target 3.0.5");
                    break;
                case V304:
                    j.createArg().setLine("-target 3.0.4");
                    break;
                default:
                    throw new HelpingBuildException("targetsdk " + targetsdk.getRelease() +" incompatible with jckit " + jckit.getRelease());

                }
            } else {
                expstringbuilder.add(getTargetSdkExportDir().toString());
            }

            // imports
            for (File imp : exps) {
                expstringbuilder.add(imp.toString());
            }
            j.createArg().setLine("-exportpath '" + expstringbuilder.toString() + "'");

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
            if (jckit.getVersion().isV3()) {
                j.createArg().setLine("-useproxyclass");
            }
            if (ints) {
                j.createArg().setLine("-i");
            }

            // determine output types
            String outputs = "CAP";
            if (output_exp != null) {
                outputs += " EXP";
            }
            if (output_jca != null) {
                outputs += " JCA";
            }
            j.createArg().setLine("-out " + outputs);

            // define applets
            for (JCApplet app : raw_applets) {
                j.createArg().setLine("-applet " + hexAID(app.aid) + " " + app.klass);
            }

            // package properties
            j.createArg().setLine(package_name + " " + hexAID(package_aid) + " " + package_version);

            // report the command
            log("command: " + j.getCommandLine(), Project.MSG_VERBOSE);

            // execute the converter
            j.execute();
        }

        @Override
        public void execute() {
            Project project = getProject();

            // perform checks
            check();

            try {
                // Compile first if necessary
                if (sources_path != null) {
                    compile();
                }

                // Create temporary folder and add to cleanup
                File applet_folder = makeTemp();

                // Construct exportpath
                ArrayList<File> exps = new ArrayList<>();

                // add imports
                for (JCImport imp : raw_imports) {
                    // Support import clauses with only jar or exp values
                    final File f;
                    if (imp.exps != null) {
                        f = new File(imp.exps).getAbsoluteFile();
                    } else {
                        try {
                            // Assume exp files in jar
                            f = makeTemp();
                            OffCardVerifier.extractExps(project.resolveFile(imp.jar), f);
                        } catch (IOException e) {
                            throw new BuildException("Can not extract EXP files from JAR", e);
                        }
                    }
                    // Avoid duplicates
                    if (!exps.contains(f)) {
                        exps.add(f);
                    }
                }

                // perform conversion
                convert(applet_folder, exps);

                // Copy results
                // Last component of the package
                String ln = package_name;
                if (ln.lastIndexOf(".") != -1) {
                    ln = ln.substring(ln.lastIndexOf(".") + 1);
                }
                // directory of package
                String pkgPath = package_name.replace(".", File.separator);
                File pkgDir = new File(applet_folder, pkgPath);
                File jcsrc = new File(pkgDir, "javacard");
                // Interesting paths inside the JC folder
                File cap = new File(jcsrc, ln + ".cap");
                File exp = new File(jcsrc, ln + ".exp");
                File jca = new File(jcsrc, ln + ".jca");

                // Verify
                if (verify) {
                    setTaskName("verify");
                    OffCardVerifier verifier = OffCardVerifier.withSDK(jckit);
                    // Add current export file
                    exps.add(exp);
                    try {
                        exps.add(getTargetSdkExportDir());
                        verifier.verify(cap, new Vector<>(exps));
                        log("Verification passed", Project.MSG_INFO);
                    } catch (VerifierError e) {
                        throw new BuildException("Verification failed: " + e.getMessage());
                    }
                }

                setTaskName("cap");
                // Copy resources to final destination
                try {
                    // check that a CAP file got created
                    if (!cap.exists()) {
                        throw new BuildException("Can not find CAP in " + jcsrc);
                    }

                    // copy CAP file
                    CAPFile capfile = CAPFile.fromBytes(Files.readAllBytes(cap.toPath()));

                    // Create output name, if not given.
                    output_cap = capFileName(capfile, output_cap);


                    // resolve output path
                    File outCap = project.resolveFile(output_cap);
                    // perform the copy
                    Files.copy(cap.toPath(), outCap.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    // report destination
                    log("CAP saved to " + outCap, Project.MSG_INFO);

                    // copy EXP file
                    if (output_exp != null) {
                        setTaskName("exp");
                        // check that an EXP file got created
                        if (!exp.exists()) {
                            throw new BuildException("Can not find EXP in " + jcsrc);
                        }
                        // resolve output directory
                        File outExp = project.resolveFile(output_exp);
                        // determine package directories
                        File outExpPkg = new File(outExp.toString(), pkgPath);
                        File outExpPkgJc = new File(outExpPkg, "javacard");
                        // create directories
                        if (!outExpPkgJc.exists()) {
                            if (!outExpPkgJc.mkdirs()) {
                                throw new HelpingBuildException("Could not create directory " + outExpPkgJc);
                            }
                        }
                        // perform the copy
                        File exp_file = new File(outExpPkgJc, exp.getName());
                        Files.copy(exp.toPath(), exp_file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        // report destination
                        log("EXP saved to " + exp_file, Project.MSG_INFO);
                        // add the export directory to the export path for verification
                        exps.add(outExp);
                    }

                    // copy JCA file
                    if (output_jca != null) {
                        setTaskName("jca");
                        // check that a JCA file got created
                        if (!jca.exists()) {
                            throw new BuildException("Can not find JCA in " + jcsrc);
                        }
                        // resolve output path
                        outCap = project.resolveFile(output_jca);
                        Files.copy(jca.toPath(), outCap.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        log("JCA saved to " + outCap.getAbsolutePath(), Project.MSG_INFO);
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
                        jarout.setDir(applet_folder);
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
                cleanTemp();
            }
        }

        private File makeTemp() {
            try {
                java.nio.file.Path p = Files.createTempDirectory("jccpro");
                File fp = p.toFile();
                temporary.add(fp);
                return fp;
            } catch (IOException e) {
                throw new RuntimeException("Can not make temporary folder", e);
            }
        }

        private String capFileName(CAPFile cap, String template) {
            String name = template;
            final String n;
            // Fallback if %n is requested with no applets
            if (cap.getAppletAIDs().size() == 1 && !cap.getFlags().contains("exports")) {
                n = className(raw_applets.get(0).klass);
            } else {
                n = cap.getPackageName();
            }

            // LFDBH-s
            name = name.replace("%H", encodeHexString(cap.getLoadFileDataHash("SHA-256")).toLowerCase());
            name = name.replace("%h", encodeHexString(cap.getLoadFileDataHash("SHA-256")).toLowerCase().substring(0, 8));
            name = name.replace("%n", n); // "common name", applet or package
            name = name.replace("%p", cap.getPackageName()); // package name
            name = name.replace("%a", cap.getPackageAID().toString()); // package AID
            name = name.replace("%j", cap.guessJavaCardVersion().orElse("unknown")); // JavaCard version
            name = name.replace("%g", cap.guessGlobalPlatformVersion().orElse("unknown")); // GlobalPlatform version
            return name;
        }
    }


    public static class JCImport {
        String exps = null;
        String jar = null;

        public void setExps(String msg) {
            exps = msg;
        }

        public void setJar(String msg) {
            jar = msg;
        }
    }

    private static String className(String fqdn) {
        String ln = fqdn;
        if (ln.lastIndexOf(".") != -1) {
            ln = ln.substring(ln.lastIndexOf(".") + 1);
        }
        return ln;
    }
}
