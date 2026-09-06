package com.nexis.monitor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable record representing a single real-time filesystem event detected by the
 * {@link DirectoryMonitor}. Carries only the affected path and the event type;
 * higher-level interpretation (hashing, baseline comparison) is left to callers.
 *
 * @param filePath  the absolute, normalized path of the affected file
 * @param eventType the type of event that occurred
 */
public record MonitorEvent(Path filePath, MonitorEventType eventType) {

    public MonitorEvent {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        filePath = filePath.toAbsolutePath().normalize();
    }
}
