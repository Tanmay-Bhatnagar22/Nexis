package com.nexis.alert;

/**
 * Represents the type of a security or integrity event detected by Nexis.
 *
 * <p>Event types are emitted by the monitoring and scanning subsystems and
 * consumed by {@link AlertManager} (CLI display) and {@link SecurityLogger}
 * (persistent logging).
 */
public enum EventType {

    /** A new file was created in the monitored directory. */
    FILE_CREATED,

    /** An existing file was modified in the monitored directory. */
    FILE_MODIFIED,

    /** A file was deleted from the monitored directory. */
    FILE_DELETED,

    /**
     * A file's SHA-256 hash differs from its stored baseline hash,
     * indicating a potential tampering or unauthorized modification.
     */
    INTEGRITY_VIOLATION,

    /** An unexpected error occurred within the real-time monitoring loop. */
    MONITORING_ERROR,

    /** An unexpected system-level error occurred outside the monitoring loop. */
    SYSTEM_ERROR
}
