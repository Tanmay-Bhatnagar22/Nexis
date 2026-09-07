package com.nexis.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;

/**
 * Handles JSON serialization and deserialization of {@link SecurityEvent} records.
 *
 * <p>Persists structured events to a JSON document (defaulting to {@code data/events.json}),
 * allowing subsequent CLI commands such as {@code nexis report} to perform forensic analysis
 * without parsing human-readable log files.
 */
public class EventStorage {

    public static final Path DEFAULT_EVENTS_PATH = Path.of("data", "events.json");
    private static final int SCHEMA_VERSION = 1;

    private final Gson gson;

    public EventStorage() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    }

    /**
     * Persists the collection of security events to a JSON file at the given destination.
     *
     * @param destination target file path
     * @param events      collection of events to persist
     * @throws IllegalArgumentException if destination or events is null
     * @throws IOException              if writing to the file fails
     */
    public void writeEvents(Path destination, Collection<SecurityEvent> events) throws IOException {
        if (destination == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }
        if (events == null) {
            throw new IllegalArgumentException("Events collection cannot be null");
        }

        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        List<EventDto> dtos = events.stream()
            .filter(Objects::nonNull)
            .map(e -> new EventDto(
                e.getTimestamp().toString(),
                e.getEventType().name(),
                e.getSeverity().name(),
                e.getFilePath() != null ? e.getFilePath().normalize().toString().replace('\\', '/') : null,
                e.getDetails()
            ))
            .toList();

        EventDocument document = new EventDocument(SCHEMA_VERSION, dtos);
        String json = gson.toJson(document);

        Files.writeString(destination, json, StandardCharsets.UTF_8);
    }

    /**
     * Reads security events from the specified JSON file.
     *
     * <p>If the file does not exist, an empty list is returned.
     *
     * @param source source file path
     * @return unmodifiable list of reconstructed {@link SecurityEvent} objects
     * @throws IllegalArgumentException if source is null
     * @throws IOException              if an I/O error occurs reading the file
     */
    public List<SecurityEvent> readEvents(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Source path cannot be null");
        }
        if (!Files.exists(source)) {
            return Collections.emptyList();
        }
        if (Files.isDirectory(source)) {
            throw new IOException("Events path is a directory, not a file: " + source);
        }

        String content = Files.readString(source, StandardCharsets.UTF_8);
        if (content == null || content.strip().isEmpty()) {
            return Collections.emptyList();
        }

        EventDocument document;
        try {
            document = gson.fromJson(content, EventDocument.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Malformed event JSON in file: " + source, e);
        }

        if (document == null || document.events == null) {
            return Collections.emptyList();
        }

        if (document.version > SCHEMA_VERSION) {
            throw new IOException(
                "Unsupported event storage schema version: " + document.version
                    + " (maximum supported: " + SCHEMA_VERSION + ")"
            );
        }

        List<SecurityEvent> result = new ArrayList<>();
        for (EventDto dto : document.events) {
            if (dto == null || dto.eventType == null || dto.severity == null || dto.details == null) {
                continue;
            }
            try {
                Instant timestamp = dto.timestamp != null ? Instant.parse(dto.timestamp) : Instant.now();
                EventType type = EventType.valueOf(dto.eventType);
                Severity severity = Severity.valueOf(dto.severity);
                Path filePath = dto.filePath != null ? Path.of(dto.filePath).normalize() : null;

                result.add(SecurityEvent.of(timestamp, type, severity, filePath, dto.details));
            } catch (Exception ignored) {
                // Skip invalid individual entries to maintain robustness
            }
        }

        return Collections.unmodifiableList(result);
    }

    private static class EventDocument {
        int version;
        List<EventDto> events;

        EventDocument(int version, List<EventDto> events) {
            this.version = version;
            this.events = events;
        }
    }

    private static class EventDto {
        String timestamp;
        String eventType;
        String severity;
        String filePath;
        String details;

        EventDto(String timestamp, String eventType, String severity, String filePath, String details) {
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.severity = severity;
            this.filePath = filePath;
            this.details = details;
        }
    }
}

