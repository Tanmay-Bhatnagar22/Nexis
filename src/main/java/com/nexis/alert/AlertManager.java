package com.nexis.alert;

import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Formats and displays {@link SecurityEvent} alerts on the CLI.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Receive a {@link SecurityEvent}</li>
 *   <li>Format it with a timestamp, severity tag, event type, path, and details</li>
 *   <li>Print it to the supplied {@link PrintWriter}</li>
 * </ul>
 *
 * <p>This class has no persistence responsibility - log file writing is handled
 * exclusively by {@link SecurityLogger}.
 *
 * <p>Example output:
 * <pre>
 * [2026-09-06 14:32:18] [INFO]     FILE_CREATED       Path: C:\watch\report.txt
 *                                                      Details: File creation detected.
 * [2026-09-06 14:35:42] [CRITICAL] INTEGRITY_VIOLATION Path: C:\watch\config.xml
 * !!                                                   Details: SHA-256 hash differs from baseline.
 * </pre>
 */
public final class AlertManager {

    /** Timestamp pattern used in CLI alert output. */
    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Width of the severity tag column, e.g. "[CRITICAL]" = 10 chars. */
    private static final int SEVERITY_WIDTH = 10;

    /** Width of the event-type column. */
    private static final int EVENT_TYPE_WIDTH = 20;

    /** Prefix printed before CRITICAL and ERROR lines to make them unmissable. */
    private static final String HIGH_SEVERITY_PREFIX = "!! ";

    private final PrintWriter out;

    /**
     * Creates an AlertManager that writes alerts to the given writer.
     *
     * @param out the CLI output writer; must not be null
     * @throws NullPointerException if out is null
     */
    public AlertManager(PrintWriter out) {
        this.out = Objects.requireNonNull(out, "PrintWriter cannot be null");
    }

    /**
     * Formats the given {@link SecurityEvent} and prints it to the CLI writer.
     *
     * <p>Format:
     * <pre>[TIMESTAMP] [SEVERITY] EVENT_TYPE  Path: &lt;path&gt;  Details: &lt;details&gt;</pre>
     *
     * <p>CRITICAL and ERROR events are additionally prefixed with {@code "!! "} to
     * make them visually distinct from INFO/WARNING lines.
     *
     * @param event the security event to display; must not be null
     * @throws NullPointerException if event is null
     */
    public void alert(SecurityEvent event) {
        Objects.requireNonNull(event, "SecurityEvent cannot be null");

        String timestamp  = DISPLAY_FORMATTER.format(event.getTimestamp());
        String severityTag = "[" + event.getSeverity().name() + "]";
        String eventTag    = event.getEventType().name();
        String pathStr     = event.getFilePath() != null
            ? "Path: " + event.getFilePath()
            : "Path: N/A";
        String detailsStr  = "Details: " + event.getDetails();

        boolean isHighSeverity = event.getSeverity() == Severity.CRITICAL
            || event.getSeverity() == Severity.ERROR;

        String prefix = isHighSeverity ? HIGH_SEVERITY_PREFIX : "   ";

        String line = String.format(
            "%s[%s] %-" + SEVERITY_WIDTH + "s %-" + EVENT_TYPE_WIDTH + "s %s  %s",
            prefix,
            timestamp,
            severityTag,
            eventTag,
            pathStr,
            detailsStr
        );

        out.println(line);
        out.flush();
    }
}
