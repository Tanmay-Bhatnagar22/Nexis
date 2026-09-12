package com.nexis.alert;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of a single security or integrity event detected by Nexis.
 *
 * <p>A {@code SecurityEvent} is produced by the monitoring and scanning subsystems
 * and consumed by {@link AlertManager} (CLI display) and {@link SecurityLogger}
 * (persistent log file). It carries all information needed for both: a precise
 * timestamp, the event type, its severity, the affected path (when applicable),
 * and a human-readable details message.
 *
 * <p>Instances are constructed via the static factory {@link #of(EventType, Severity, Path, String)}
 * or {@link #of(EventType, Severity, String)} (for path-less system errors).
 */
public final class SecurityEvent {

    private final Instant timestamp;
    private final EventType eventType;
    private final Severity severity;
    private final Path filePath;   // nullable - not all events have an associated path
    private final String details;

    /**
     * Private constructor. Use the static factory methods to create instances.
     */
    private SecurityEvent(Instant timestamp, EventType eventType, Severity severity, Path filePath, String details) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.severity  = Objects.requireNonNull(severity,  "severity cannot be null");
        this.filePath  = filePath != null ? filePath.toAbsolutePath().normalize() : null;
        this.details   = Objects.requireNonNull(details, "details cannot be null");
    }

    private SecurityEvent(EventType eventType, Severity severity, Path filePath, String details) {
        this(Instant.now(), eventType, severity, filePath, details);
    }

    // -------------------------------------------------------------------------
    // Static factories
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code SecurityEvent} with an associated file path.
     *
     * @param eventType the type of event
     * @param severity  the severity level
     * @param filePath  the affected file path (must not be null)
     * @param details   a human-readable description of the event
     * @return a new, immutable SecurityEvent
     * @throws NullPointerException if eventType, severity, filePath, or details is null
     */
    public static SecurityEvent of(EventType eventType, Severity severity,
                                   Path filePath, String details) {
        Objects.requireNonNull(filePath, "filePath cannot be null for path-specific events");
        return new SecurityEvent(eventType, severity, filePath, details);
    }

    /**
     * Creates a {@code SecurityEvent} without an associated file path.
     * Suitable for system-level or monitoring errors that are not tied to a specific file.
     *
     * @param eventType the type of event
     * @param severity  the severity level
     * @param details   a human-readable description of the event
     * @return a new, immutable SecurityEvent
     * @throws NullPointerException if eventType, severity, or details is null
     */
    public static SecurityEvent of(EventType eventType, Severity severity, String details) {
        return new SecurityEvent(eventType, severity, null, details);
    }

    /**
     * Creates a {@code SecurityEvent} with an explicit timestamp and an optional file path.
     * Suitable for deserializing stored events or testing time-sensitive scenarios.
     *
     * @param timestamp the instant at which the event occurred
     * @param eventType the type of event
     * @param severity  the severity level
     * @param filePath  the affected file path, or null
     * @param details   a human-readable description of the event
     * @return a new, immutable SecurityEvent
     * @throws NullPointerException if timestamp, eventType, severity, or details is null
     */
    public static SecurityEvent of(Instant timestamp, EventType eventType, Severity severity,
                                   Path filePath, String details) {
        return new SecurityEvent(timestamp, eventType, severity, filePath, details);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the instant at which this event was created.
     *
     * @return event timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the type of security or integrity event.
     *
     * @return event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Returns the severity level of this event.
     *
     * @return severity
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Returns the affected file path, or {@code null} if this event is not
     * associated with a specific file (e.g. {@code SYSTEM_ERROR}).
     *
     * @return absolute normalized file path, or null
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Returns the human-readable details message for this event.
     *
     * @return details string
     */
    public String getDetails() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SecurityEvent that)) {
            return false;
        }
        return Objects.equals(timestamp, that.timestamp)
            && eventType == that.eventType
            && severity == that.severity
            && Objects.equals(filePath, that.filePath)
            && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, eventType, severity, filePath, details);
    }

    @Override
    public String toString() {
        return "SecurityEvent{"
            + "type=" + eventType
            + ", severity=" + severity
            + ", path=" + filePath
            + ", details='" + details + '\''
            + '}';
    }
}
