package com.nexis.alert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Persists {@link SecurityEvent} records to a log file on disk.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Append one structured log line per {@link SecurityEvent} to {@code logs/nexis.log}</li>
 *   <li>Create the log file and its parent directory automatically if absent</li>
 *   <li>Handle {@link IOException} safely - a logging failure is reported to
 *       {@code System.err} but must never propagate to crash the monitoring engine</li>
 * </ul>
 *
 * <p>Log format (pipe-delimited, one entry per line):
 * <pre>
 * 2026-09-06 14:32:18 | INFO     | FILE_CREATED       | C:\watch\report.txt | File creation detected.
 * 2026-09-06 14:35:42 | CRITICAL | INTEGRITY_VIOLATION | C:\watch\config.xml | SHA-256 hash differs from baseline.
 * </pre>
 *
 * <p>This class has no CLI display responsibility - alert formatting is handled
 * exclusively by {@link AlertManager}.
 */
public final class SecurityLogger {

    /** Default log file path, relative to the working directory. */
    public static final Path DEFAULT_LOG_PATH = Path.of("logs", "nexis.log");

    /** Timestamp pattern used in log output. */
    private static final DateTimeFormatter LOG_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Width of the severity column in the log line. */
    private static final int SEVERITY_WIDTH = 8;

    /** Width of the event-type column in the log line. */
    private static final int EVENT_TYPE_WIDTH = 20;

    private final Path logPath;

    /**
     * Creates a SecurityLogger writing to the default log path ({@code logs/nexis.log}).
     */
    public SecurityLogger() {
        this(DEFAULT_LOG_PATH);
    }

    /**
     * Creates a SecurityLogger writing to the specified log path.
     * Useful for testing or alternate log locations.
     *
     * @param logPath the path of the log file to write to; must not be null
     * @throws NullPointerException if logPath is null
     */
    public SecurityLogger(Path logPath) {
        this.logPath = Objects.requireNonNull(logPath, "logPath cannot be null");
    }

    /**
     * Appends a single log entry for the given {@link SecurityEvent} to the log file.
     *
     * <p>If the log directory or file does not yet exist, they are created automatically.
     * If any {@link IOException} occurs during logging, it is reported to {@code System.err}
     * and the method returns normally - it never throws, so a logging failure cannot
     * interrupt the monitoring engine.
     *
     * @param event the security event to log; must not be null
     */
    public void log(SecurityEvent event) {
        Objects.requireNonNull(event, "SecurityEvent cannot be null");

        String line = formatLogLine(event);

        try {
            ensureLogDirectoryExists();
            Files.writeString(
                logPath,
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            // Logging failure must never crash the monitoring engine.
            System.err.println("[NEXIS] Warning: Failed to write security log entry - " + e.getMessage());
        }
    }

    /**
     * Returns the path of the log file this logger writes to.
     *
     * @return log file path
     */
    public Path getLogPath() {
        return logPath;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Formats a single log line in the pipe-delimited format:
     * {@code TIMESTAMP | SEVERITY | EVENT_TYPE | PATH | DETAILS}
     */
    private static String formatLogLine(SecurityEvent event) {
        String timestamp  = LOG_FORMATTER.format(event.getTimestamp());
        String severity   = String.format("%-" + SEVERITY_WIDTH + "s", event.getSeverity().name());
        String eventType  = String.format("%-" + EVENT_TYPE_WIDTH + "s", event.getEventType().name());
        String pathStr    = event.getFilePath() != null ? event.getFilePath().toString() : "N/A";
        String details    = event.getDetails();

        return timestamp + " | " + severity + " | " + eventType + " | " + pathStr + " | " + details;
    }

    /**
     * Ensures the parent directory of the log file exists, creating it if necessary.
     *
     * @throws IOException if directory creation fails
     */
    private void ensureLogDirectoryExists() throws IOException {
        Path parent = logPath.toAbsolutePath().normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
