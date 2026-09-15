// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SDKLogger {
    // java.util.logging collects an unreferenced logger together with its settings
    private static final Logger SDK_LOGGER = Logger.getLogger("com.sun.javacard");

    // Written on first use
    private static final class Config {
        static final String PATH = configure();
    }

    private static String configure() {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("_ANT_JAVACARD_LOGHACK", "true"))) {
            // v25.0+ tools reload logging.properties from tools.jar unless java.util.logging.config.file is set
            try {
                // deleteOnExit runs in reverse order of registration
                Path logdir = Files.createTempDirectory("jccpro");
                logdir.toFile().deleteOnExit();
                Path f = logdir.resolve("logging.properties");
                f.toFile().deleteOnExit();
                String conf = "handlers = java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.SimpleFormatter.format=[ %4$s ] %5$s%6$s%n\n";
                Files.write(f, conf.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                String logconf = f.toAbsolutePath().normalize().toString();
                if (System.getProperty("java.util.logging.config.file") == null) {
                    System.setProperty("java.util.logging.config.file", logconf);
                }
                return logconf;
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not write temporary logging configuration: " + e.getMessage());
            }
        } else {
            System.err.println("Loghack disabled");
        }
        return null;
    }

    private SDKLogger() {
    }

    // The logging.properties a forked SDK tool reads at JVM startup
    public static Optional<String> loggingConfig() {
        return Optional.ofNullable(Config.PATH);
    }

    private static final ReentrantLock SDK_LOCK = new ReentrantLock();

    // Holds the JVM-global SDK logger until the returned SDKLog is closed
    public static SDKLog capture(Handler handler) {
        return new SDKLog(handler);
    }

    public static final class SDKLog implements AutoCloseable {
        private final Handler handler;
        private final boolean parenthandlers;
        private final Level sdklevel;

        SDKLog(Handler handler) {
            SDK_LOCK.lock();
            this.handler = handler;
            this.parenthandlers = SDK_LOGGER.getUseParentHandlers();
            this.sdklevel = SDK_LOGGER.getLevel();
            if (handler != null) {
                SDK_LOGGER.addHandler(handler);
                SDK_LOGGER.setUseParentHandlers(false);
                SDK_LOGGER.setLevel(Level.ALL);
            }
        }

        @Override
        public void close() {
            try {
                if (handler != null) {
                    SDK_LOGGER.removeHandler(handler);
                    SDK_LOGGER.setUseParentHandlers(parenthandlers);
                    SDK_LOGGER.setLevel(sdklevel);
                }
            } finally {
                SDK_LOCK.unlock();
            }
        }
    }
}
