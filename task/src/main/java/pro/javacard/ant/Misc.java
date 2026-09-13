// SPDX-FileCopyrightText: 2015 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import pro.javacard.capfile.CAPFile;
import pro.javacard.capfile.HexUtils;
import pro.javacard.sdk.OffCardVerifier;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

final class Misc {

    // Puts java.util.logging records of an in-process SDK tool into the ant log
    static final class AntLog extends Handler {
        // SDK log records carry a ResourceBundle key instead of the text
        private final SimpleFormatter formatter = new SimpleFormatter();
        private final Task task;
        boolean failed = false;

        AntLog(Task task) {
            this.task = task;
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                failed = true;
            }
            task.log(formatter.formatMessage(record), antLevel(record.getLevel()));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static int antLevel(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return Project.MSG_ERR;
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return Project.MSG_WARN;
        }
        if (level.intValue() >= Level.INFO.intValue()) {
            return Project.MSG_INFO;
        }
        return Project.MSG_VERBOSE;
    }

    static int getCurrentJDKVersion() {
        String v = System.getProperty("java.version", "0.0.0");
        if (v.startsWith("1.8.")) {
            v = "8." + v.substring(4);
        }
        // An early access build reads 26-ea or 24-loom+1-15
        return Integer.parseInt(v.split("\\D", 2)[0]);
    }

    // foo.bar.Baz -> Baz; Foo -> Foo
    static String lastName(String fqdn) {
        String ln = fqdn;
        if (ln.lastIndexOf(".") != -1) {
            ln = ln.substring(ln.lastIndexOf(".") + 1);
        }
        return ln;
    }

    // Every folder made outside ANT_JAVACARD_TMP is recorded in the caller's list for cleanTemp
    static Path makeTemp(String sub, List<Path> temporary) {
        try {
            if (System.getenv("ANT_JAVACARD_TMP") != null) {
                Path tmp = Paths.get(System.getenv("ANT_JAVACARD_TMP"), sub).toAbsolutePath().normalize();
                // Removes files an earlier run left in this folder
                OffCardVerifier.rmminusrf(tmp);
                Files.createDirectories(tmp);
                return tmp;
            } else {
                Path p = Files.createTempDirectory("jccpro");
                temporary.add(p);
                return p;
            }
        } catch (IOException e) {
            throw new RuntimeException("Can not make temporary folder", e);
        }
    }

    static String commonName(CAPFile cap, String knownClass) {
        if (cap.getAppletAIDs().size() == 1 && !cap.getFlags().contains("exports")) {
            String className = knownClass == null ? cap.getApplets().values().iterator().next() : knownClass;
            if (className != null) {
                return lastName(className);
            }
        }
        return cap.getPackageName();
    }

    static String capFileName(CAPFile cap, String template) {
        return capFileName(cap, template, null);
    }

    static String capFileName(CAPFile cap, String template, String knownClass) {
        String commonName = commonName(cap, knownClass);
        String hash = HexUtils.bin2hex(cap.getLoadFileDataHash("SHA-256")).toLowerCase();

        String name = template;
        name = name.replace("%H", hash);
        name = name.replace("%h", hash.substring(0, 8));
        name = name.replace("%n", commonName);
        name = name.replace("%p", cap.getPackageName());
        name = name.replace("%a", cap.getPackageAID().toString());
        name = name.replace("%v", "v" + cap.getPackageVersion());
        name = name.replace("%j", cap.guessJavaCardVersion().orElse("unknown"));
        name = name.replace("%g", cap.guessGlobalPlatformVersion().orElse("unknown"));
        name = name.replace("%J", String.format("jdk%d", getCurrentJDKVersion()));
        return name;
    }

    static void cleanTemp(List<Path> temporary) {
        // A manually set temporary path is kept for debugging
        if (System.getenv("ANT_JAVACARD_TMP") != null) {
            return;
        }

        if (Boolean.parseBoolean(System.getenv().getOrDefault("_ANT_JAVACARD_LITTER", "false"))) {
            System.err.println("Littering filesystem due to _ANT_JAVACARD_LITTER");
            return;
        }

        for (Path f : temporary) {
            if (Files.exists(f)) {
                OffCardVerifier.rmminusrf(f);
            }
        }
    }
}
