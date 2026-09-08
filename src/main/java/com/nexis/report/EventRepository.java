package com.nexis.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;

/**
 * In-memory repository for retaining and querying structured {@link SecurityEvent} objects.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Store {@link SecurityEvent} objects produced during monitoring or scanning</li>
 *   <li>Provide fast, filtered retrieval by severity, event type, or affected file path</li>
 *   <li>Prevent modification of internal state through defensive unmodifiable views</li>
 *   <li>Support thread-safe access from concurrent monitoring threads</li>
 *   <li>Optionally persist to and restore from structured storage ({@code data/events.json})</li>
 * </ul>
 *
 * <p>This class strictly focuses on event storage and querying. It does not perform CLI formatting,
 * logging to log files, baseline comparison, or hashing.
 */
public class EventRepository {

    public static final Path DEFAULT_EVENTS_PATH = EventStorage.DEFAULT_EVENTS_PATH;

    private final List<SecurityEvent> events = new ArrayList<>();
    private final EventStorage storage;
    private final Path storagePath;

    /**
     * Creates an in-memory EventRepository backed by the default storage path.
     */
    public EventRepository() {
        this(DEFAULT_EVENTS_PATH, new EventStorage());
    }

    /**
     * Creates an in-memory EventRepository with a custom storage path.
     *
     * @param storagePath path to the JSON storage file; must not be null
     */
    public EventRepository(Path storagePath) {
        this(storagePath, new EventStorage());
    }

    /**
     * Creates an EventRepository with a custom storage path and storage engine.
     *
     * @param storagePath path to the JSON storage file; must not be null
     * @param storage     storage persistence engine; must not be null
     */
    public EventRepository(Path storagePath, EventStorage storage) {
        this.storagePath = Objects.requireNonNull(storagePath, "Storage path cannot be null");
        this.storage = Objects.requireNonNull(storage, "EventStorage cannot be null");
    }

    /**
     * Adds a {@link SecurityEvent} to the repository.
     *
     * @param event the event to add; must not be null
     * @throws NullPointerException if event is null
     */
    public synchronized void add(SecurityEvent event) {
        Objects.requireNonNull(event, "SecurityEvent cannot be null");
        events.add(event);
    }

    /**
     * Adds all provided {@link SecurityEvent} objects to the repository.
     *
     * @param newEvents collection of events to add; must not be null
     * @throws NullPointerException if newEvents or any element is null
     */
    public synchronized void addAll(Collection<SecurityEvent> newEvents) {
        Objects.requireNonNull(newEvents, "Events collection cannot be null");
        for (SecurityEvent e : newEvents) {
            Objects.requireNonNull(e, "SecurityEvent cannot be null");
            events.add(e);
        }
    }

    /**
     * Returns an unmodifiable snapshot of all stored security events.
     *
     * @return unmodifiable list of all events
     */
    public synchronized List<SecurityEvent> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Returns all events matching the specified severity level.
     *
     * @param severity the severity level to filter by; must not be null
     * @return unmodifiable list of matching events
     * @throws NullPointerException if severity is null
     */
    public synchronized List<SecurityEvent> findBySeverity(Severity severity) {
        Objects.requireNonNull(severity, "Severity cannot be null");
        return events.stream()
            .filter(e -> e.getSeverity() == severity)
            .toList();
    }

    /**
     * Returns all events matching the specified event type.
     *
     * @param type the event type to filter by; must not be null
     * @return unmodifiable list of matching events
     * @throws NullPointerException if type is null
     */
    public synchronized List<SecurityEvent> findByType(EventType type) {
        Objects.requireNonNull(type, "EventType cannot be null");
        return events.stream()
            .filter(e -> e.getEventType() == type)
            .toList();
    }

    /**
     * Returns all events associated with the given file path.
     *
     * <p>Matching is performed against the normalized absolute path.
     *
     * @param path the path to filter by; must not be null
     * @return unmodifiable list of matching events
     * @throws NullPointerException if path is null
     */
    public synchronized List<SecurityEvent> findByPath(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        Path normalizedTarget = path.toAbsolutePath().normalize();
        return events.stream()
            .filter(e -> e.getFilePath() != null && e.getFilePath().equals(normalizedTarget))
            .toList();
    }

    /**
     * Multi-criteria filter query. Returns events matching all non-null criteria.
     *
     * @param severity optional severity filter (null matches any)
     * @param type     optional event type filter (null matches any)
     * @param path     optional path filter (null matches any)
     * @return unmodifiable list of matching events
     */
    public synchronized List<SecurityEvent> find(Severity severity, EventType type, Path path) {
        Path normalizedPath = path != null ? path.toAbsolutePath().normalize() : null;

        return events.stream()
            .filter(e -> severity == null || e.getSeverity() == severity)
            .filter(e -> type == null || e.getEventType() == type)
            .filter(e -> normalizedPath == null || (e.getFilePath() != null && e.getFilePath().equals(normalizedPath)))
            .toList();
    }

    /**
     * Returns the total number of stored events.
     *
     * @return count of events
     */
    public synchronized int size() {
        return events.size();
    }

    /**
     * Checks if the repository contains zero events.
     *
     * @return true if empty, false otherwise
     */
    public synchronized boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * Returns the count of events with the given severity level.
     *
     * @param severity severity level to count
     * @return count of matching events
     */
    public synchronized int countBySeverity(Severity severity) {
        if (severity == null) {
            return 0;
        }
        return (int) events.stream().filter(e -> e.getSeverity() == severity).count();
    }

    /**
     * Returns the count of events with the given event type.
     *
     * @param type event type to count
     * @return count of matching events
     */
    public synchronized int countByType(EventType type) {
        if (type == null) {
            return 0;
        }
        return (int) events.stream().filter(e -> e.getEventType() == type).count();
    }

    /**
     * Clears all in-memory events from the repository.
     */
    public synchronized void clear() {
        events.clear();
    }

    /**
     * Persists all currently held events to the configured storage path.
     *
     * @throws IOException if saving fails
     */
    public synchronized void save() throws IOException {
        save(this.storagePath);
    }

    /**
     * Persists all currently held events to the specified target path.
     *
     * @param destination destination file path
     * @throws IOException if saving fails
     */
    public synchronized void save(Path destination) throws IOException {
        storage.writeEvents(destination, events);
    }

    /**
     * Loads events from the configured storage path, replacing current in-memory events.
     *
     * @throws IOException if an error occurs reading the file
     */
    public synchronized void load() throws IOException {
        load(this.storagePath);
    }

    /**
     * Loads events from the specified path, replacing current in-memory events.
     *
     * @param source source file path
     * @throws IOException if an error occurs reading the file
     */
    public synchronized void load(Path source) throws IOException {
        List<SecurityEvent> loaded = storage.readEvents(source);
        events.clear();
        events.addAll(loaded);
    }

    /**
     * Convenience factory that creates an EventRepository and loads events from
     * the default events path. If the file does not exist, an empty repository
     * is returned.
     *
     * @return EventRepository instance
     * @throws IOException if reading an existing events file fails (e.g. malformed JSON or unsupported schema)
     */
    public static EventRepository loadOrDefault() throws IOException {
        return loadOrDefault(DEFAULT_EVENTS_PATH);
    }

    /**
     * Convenience factory that creates an EventRepository backed by the specified
     * storage path and loads its events. If the file does not exist, an empty
     * repository is returned.
     *
     * @param storagePath path to the JSON storage file; must not be null
     * @return EventRepository instance
     * @throws IOException if reading an existing events file fails (e.g. malformed JSON or unsupported schema)
     */
    public static EventRepository loadOrDefault(Path storagePath) throws IOException {
        EventRepository repo = new EventRepository(storagePath);
        repo.load();
        return repo;
    }
}

