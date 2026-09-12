package com.nexis.alert;

/**
 * Represents the severity level of a {@link SecurityEvent}.
 *
 * <p>Severity levels are used by {@link AlertManager} to determine the visual
 * prominence of a CLI alert, and by {@link SecurityLogger} to classify
 * log entries.
 */
public enum Severity {

    /**
     * Informational event - no security concern, routine observation.
     * Example: a new file was created in a watched directory.
     */
    INFO,

    /**
     * Warning-level event - noteworthy but not necessarily a security breach.
     * Example: a monitored file was modified or deleted.
     */
    WARNING,

    /**
     * Critical-level event - a confirmed security or integrity violation.
     * Example: a file's hash differs from its baseline (tampering suspected).
     */
    CRITICAL,

    /**
     * Error-level event - an unexpected failure in the monitoring or logging
     * subsystem that prevented normal operation.
     */
    ERROR
}
