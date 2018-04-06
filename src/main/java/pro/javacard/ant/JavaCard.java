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

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class JavaCard extends Task {
    // This code has been taken from Apache commons-codec 1.7 (License: Apache
    // 2.0)
    private static final char[] LOWER_HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private String master_jckit_path = null;
    private String master_java_version = null;
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

    public void setJavaversion(String msg) {
        master_java_version = msg;
    }

    public JCCap createCap() {
        JCCap pkg = new JCCap();
        packages.add(pkg);
        return pkg;
    }

    @Override
    public void execute() {
        for (JCCap p : packages) {
            p.execute();
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
                throw new BuildException("Not a correct applet AID: " + e.getMessage());
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
        private JCKit jckit = null;
        private String classes_path = null;
        private String sources_path = null;
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
        private String java_version = null;
        private boolean verify = true;
        private boolean debug = false;
        private boolean ints = false;
        private List<File> temporary = new ArrayList<>();

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

        public void setVerify(boolean arg) {
            verify = arg;
        }

        public void setDebug(boolean arg) {
            debug = arg;
        }

        public void setInts(boolean arg) {
            ints = arg;
        }

        public void setAID(String msg) {
            try {
                package_aid = stringToBin(msg);
                if (package_aid.length < 5 || package_aid.length > 16)
                    throw new BuildException("Package AID must be between 5 and 16 bytes: " + package_aid.length);

            } catch (IllegalArgumentException e) {
                throw new BuildException("Not a correct package AID: " + e.getMessage());
            }
        }

        /**
         * Many applets inside one package
         */
        public JCApplet createApplet() {
            JCApplet applet = new JCApplet();
            raw_applets.add(applet);
            return applet;
        }

        /**
         * Many imports inside one package
         */
        public JCImport createImport() {
            JCImport imp = new JCImport();
            raw_imports.add(imp);
            return imp;
        }

        // To support usage from Gradle, where import is a reserved name
        public JCImport createJimport() {
            return this.createImport();
        }

        private JCKit findSDK() {
            // try configuration first
            if(jckit_path != null) {
                return JCKit.detectSDK(jckit_path);
            }
            if(master_jckit_path != null) {
                return JCKit.detectSDK(master_jckit_path);
            }
            // now check via ant property
            String propPath = getProject().getProperty("jc.home");
            if(propPath != null) {
                return JCKit.detectSDK(propPath);
            }
            // finally via the environment
            String envPath = System.getenv("JC_HOME");
            if(envPath != null) {
                return JCKit.detectSDK(envPath);
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
            } else {
                log("INFO: using JavaCard " + jckit.getVersion() + " SDK in " + jckit.getRoot(), Project.MSG_INFO);
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

            // Construct applets and fill in missing bits from package info, if
            int applet_counter = 0;
            // necessary
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
                    String pkgname = a.klass.substring(0, a.klass.lastIndexOf("."));
                    log("Setting package name to " + pkgname, Project.MSG_INFO);
                    package_name = pkgname;
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
                        log("INFO: generated applet AID: " + hexAID(a.aid) + " for " + a.klass, Project.MSG_INFO);
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

            // Check output file
            if (output_cap == null) {
                throw new HelpingBuildException("Must specify output file");
            }
            // Nice info
            log("Building CAP with " + applet_counter + " applet" + (applet_counter > 1 ? "s" : "") + " from package " + package_name, Project.MSG_INFO);
            for (JCApplet app : raw_applets) {
                log(app.klass + " " + encodeHexString(app.aid), Project.MSG_INFO);
            }
        }

        private void compile() {
            Project project = getProject();
            setTaskName("compile");

            // construct javac task
            Javac j = new Javac();
            j.setProject(project);
            j.setTaskName("compile");

            j.setSrcdir(new Path(project, sources_path));

            // determine output directory
            File tmp;
            if (classes_path != null) {
                // if specified use that
                tmp = project.resolveFile(classes_path);
                if (!tmp.exists()) {
                    if (!tmp.mkdir())
                        throw new BuildException("Could not create temporary folder " + tmp.getAbsolutePath());
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
            if(java_version != null) {
                javaVersion = java_version;
            } else {
                if(master_java_version != null) {
                    javaVersion = master_java_version;
                }
            }
            j.setTarget(javaVersion);
            j.setSource(javaVersion);
            if (jckit.isVersion(JCKit.Version.V21)) {
                // Always set debug to disable "contains local variables,
                // but not local variable table." messages
                j.setDebug(true);
            }
            j.setIncludeantruntime(false);
            j.createCompilerArg().setValue("-Xlint");
            j.createCompilerArg().setValue("-Xlint:-options");
            j.createCompilerArg().setValue("-Xlint:-serial");

            j.setFailonerror(true);
            j.setFork(true);

            // set classpath
            Path cp = j.createClasspath();
            String api = jckit.getApiJar().toString();
            cp.append(new Path(project, api));
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
            for(File jar: jckit.getToolJars()) {
                cp.append(new Path(project, jar.getPath()));
            }
            j.setClasspath(cp);
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
            if (jckit.isVersion(JCKit.Version.V3)) {
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
            if (!verify) {
                j.createArg().setLine("-noverify");
            }
            if (jckit.isVersion(JCKit.Version.V3)) {
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

        private void verify(List<File> exps) {
			Project project = getProject();
            setTaskName("verify");

            // collect all export files
            final ArrayList<File> expfiles = new ArrayList<>();
            try {
                for (File e : exps) {
                    Files.walkFileTree(e.toPath(), new SimpleFileVisitor<java.nio.file.Path>() {
                        @Override
                        public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs)
                                throws IOException {
                            if (file.toString().endsWith(".exp")) {
                                expfiles.add(file.toFile());
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            } catch (IOException e) {
                log("Could not find .exp files: " + e.getMessage(), Project.MSG_ERR);
                return;
            }

            // construct java task
            Java j = new Java(this);
            j.setTaskName("verify");
            j.setFailonerror(true);
            j.setFork(true);

            // add classpath for SDK tools
            addKitClasses(j);
            // set main class
            j.setClassname("com.sun.javacard.offcardverifier.Verifier");

            // not verbose for now
            //j.createArg().setLine("-verbose");
            j.createArg().setLine("-nobanner");

            // export files for verification
            for (File exp : expfiles) {
                j.createArg().setFile(exp);
            }

            // cap file for verification
            j.createArg().setFile(project.resolveFile(output_cap));

            // report the command
            log("command: " + j.getCommandLine(), Project.MSG_VERBOSE);

            // perform verification
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
                exps.add(jckit.getExportDir());

                // add imports
                for (JCImport imp : raw_imports) {
                    // Support import clauses with only jar or exp values
                    if (imp.exps != null) {
                        File f = new File(imp.exps).getAbsoluteFile();
                        // Avoid duplicates
                        if (!exps.contains(f)) {
                            exps.add(f);
                        }
                    }
                }

                // perform conversion
                convert(applet_folder, exps);

                // Copy results
                if (output_cap != null || output_exp != null || output_jca != null || output_jar != null) {
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

                    try {
                        // copy CAP file
                        setTaskName("cap");
                        // check that a CAP file got created
                        if (!cap.exists()) {
                            throw new BuildException("Can not find CAP in " + jcsrc);
                        }
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
                            File exp_file =  new File(outExpPkgJc, exp.getName());
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
                            log("JAR created at " + outJar.getAbsolutePath(), Project.MSG_INFO);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new BuildException("Can not copy output CAP, EXP or JCA", e);
                    }
                }

                if (verify) {
                    verify(exps);
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

        private void cleanTemp() {
            // Clean temporary files.
            for (File f : temporary) {
                if (f.exists()) {
                    rmminusrf(f.toPath());
                }
            }
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

}
