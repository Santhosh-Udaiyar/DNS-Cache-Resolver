package com.dns.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe logging utility for DNS operations.
 * Writes formatted log messages to standard output, a log file in logs/dns.log,
 * and notifies registered GUI listeners in real-time.
 */
public final class Logger {

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "logs/dns.log";
    private static final Object LOCK = new Object();
    private static PrintWriter fileWriter;
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

    static {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            fileWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
        } catch (IOException e) {
            System.err.println("Failed to initialize file logger: " + e.getMessage());
        }
    }

    private Logger() {
    }

    public static void addListener(Consumer<String> listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    private static void log(Level level, String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String threadName = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%-5s] [%s] %s", timestamp, level.name(), threadName, message);

        synchronized (LOCK) {
            if (level == Level.ERROR) {
                System.err.println(formatted);
                if (throwable != null) {
                    throwable.printStackTrace(System.err);
                }
            } else {
                System.out.println(formatted);
            }

            if (fileWriter != null) {
                fileWriter.println(formatted);
                if (throwable != null) {
                    throwable.printStackTrace(fileWriter);
                }
                fileWriter.flush();
            }
        }

        for (Consumer<String> listener : LISTENERS) {
            try {
                listener.accept(formatted);
            } catch (Exception ignored) {
            }
        }
    }
}
