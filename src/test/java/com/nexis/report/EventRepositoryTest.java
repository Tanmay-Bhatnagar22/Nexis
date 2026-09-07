package com.nexis.report;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;

class EventRepositoryTest {

    private EventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EventRepository();
    }

    @Test
    @DisplayName("1. Empty repository returns size 0, isEmpty true, and empty queries")
    void emptyRepository() {
        assertEquals(0, repository.size());
        assertTrue(repository.isEmpty());
        assertTrue(repository.getAll().isEmpty());
        assertTrue(repository.findBySeverity(Severity.CRITICAL).isEmpty());
        assertTrue(repository.findByType(EventType.FILE_CREATED).isEmpty());
        assertTrue(repository.findByPath(Path.of("test.txt")).isEmpty());
        assertEquals(0, repository.countBySeverity(Severity.CRITICAL));
        assertEquals(0, repository.countByType(EventType.FILE_CREATED));
    }

    @Test
    @DisplayName("2. Adding a single event updates size, isEmpty, and query results")
    void addSingleEvent() {
        Path path = Path.of("sample.txt");
        SecurityEvent event = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, path, "Created");

        repository.add(event);

        assertEquals(1, repository.size());
        assertFalse(repository.isEmpty());
        assertEquals(1, repository.getAll().size());
        assertEquals(event, repository.getAll().get(0));
        assertEquals(1, repository.countBySeverity(Severity.INFO));
        assertEquals(1, repository.countByType(EventType.FILE_CREATED));
    }

    @Test
    @DisplayName("3. Adding multiple events preserves all entries in insertion order")
    void addMultipleEvents() {
        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "A");
        SecurityEvent e2 = SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, Path.of("b.txt"), "B");
        SecurityEvent e3 = SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("c.txt"), "C");

        repository.addAll(List.of(e1, e2, e3));

        assertEquals(3, repository.size());
        assertEquals(List.of(e1, e2, e3), repository.getAll());
    }

    @Test
    @DisplayName("4. Correct event counts by total, severity, and event type")
    void correctEventCounts() {
        repository.add(SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("1.txt"), "1"));
        repository.add(SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("2.txt"), "2"));
        repository.add(SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, Path.of("3.txt"), "3"));
        repository.add(SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("4.txt"), "4"));

        assertEquals(4, repository.size());
        assertEquals(2, repository.countBySeverity(Severity.INFO));
        assertEquals(1, repository.countBySeverity(Severity.WARNING));
        assertEquals(1, repository.countBySeverity(Severity.CRITICAL));
        assertEquals(0, repository.countBySeverity(Severity.ERROR));

        assertEquals(2, repository.countByType(EventType.FILE_CREATED));
        assertEquals(1, repository.countByType(EventType.FILE_MODIFIED));
        assertEquals(1, repository.countByType(EventType.INTEGRITY_VIOLATION));
        assertEquals(0, repository.countByType(EventType.FILE_DELETED));
    }

    @Test
    @DisplayName("5. Filtering by severity returns only matching events")
    void filterBySeverity() {
        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "A");
        SecurityEvent e2 = SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("b.txt"), "B");
        SecurityEvent e3 = SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("c.txt"), "C");

        repository.addAll(List.of(e1, e2, e3));

        List<SecurityEvent> criticalEvents = repository.findBySeverity(Severity.CRITICAL);
        assertEquals(2, criticalEvents.size());
        assertTrue(criticalEvents.contains(e2));
        assertTrue(criticalEvents.contains(e3));
        assertFalse(criticalEvents.contains(e1));

        List<SecurityEvent> warningEvents = repository.findBySeverity(Severity.WARNING);
        assertTrue(warningEvents.isEmpty());
    }

    @Test
    @DisplayName("6. Filtering by event type returns only matching events")
    void filterByType() {
        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "A");
        SecurityEvent e2 = SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, Path.of("b.txt"), "B");
        SecurityEvent e3 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("c.txt"), "C");

        repository.addAll(List.of(e1, e2, e3));

        List<SecurityEvent> createdEvents = repository.findByType(EventType.FILE_CREATED);
        assertEquals(2, createdEvents.size());
        assertTrue(createdEvents.contains(e1));
        assertTrue(createdEvents.contains(e3));
        assertFalse(createdEvents.contains(e2));
    }

    @Test
    @DisplayName("7. Filtering by path matches normalized absolute paths")
    void filterByPath() {
        Path target = Path.of("data", "target.txt");
        Path other = Path.of("data", "other.txt");

        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, target, "Created");
        SecurityEvent e2 = SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, target, "Modified");
        SecurityEvent e3 = SecurityEvent.of(EventType.FILE_DELETED, Severity.WARNING, other, "Deleted");

        repository.addAll(List.of(e1, e2, e3));

        List<SecurityEvent> targetEvents = repository.findByPath(target);
        assertEquals(2, targetEvents.size());
        assertTrue(targetEvents.contains(e1));
        assertTrue(targetEvents.contains(e2));

        List<SecurityEvent> otherEvents = repository.findByPath(other);
        assertEquals(1, otherEvents.size());
        assertTrue(otherEvents.contains(e3));

        assertTrue(repository.findByPath(Path.of("nonexistent.txt")).isEmpty());
    }

    @Test
    @DisplayName("8. Returned collections cannot corrupt internal repository state")
    void returnedCollectionsAreImmutable() {
        SecurityEvent event = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "A");
        repository.add(event);

        List<SecurityEvent> all = repository.getAll();
        SecurityEvent intruder = SecurityEvent.of(EventType.FILE_DELETED, Severity.WARNING, Path.of("b.txt"), "B");

        assertThrows(UnsupportedOperationException.class, () -> all.add(intruder));
        assertEquals(1, repository.size());

        List<SecurityEvent> bySeverity = repository.findBySeverity(Severity.INFO);
        assertThrows(UnsupportedOperationException.class, () -> bySeverity.add(intruder));
        assertEquals(1, repository.size());
    }

    @Test
    @DisplayName("9. Multi-criteria find supports combining severity, type, and path")
    void multiCriteriaFind() {
        Path path1 = Path.of("file1.txt");
        Path path2 = Path.of("file2.txt");

        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, path1, "1");
        SecurityEvent e2 = SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, path1, "2");
        SecurityEvent e3 = SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, path2, "3");

        repository.addAll(List.of(e1, e2, e3));

        // Filter by severity and type
        List<SecurityEvent> found = repository.find(Severity.CRITICAL, EventType.INTEGRITY_VIOLATION, null);
        assertEquals(2, found.size());

        // Filter by severity, type, and path
        found = repository.find(Severity.CRITICAL, EventType.INTEGRITY_VIOLATION, path1);
        assertEquals(1, found.size());
        assertEquals(e2, found.get(0));

        // Filter by path only
        found = repository.find(null, null, path1);
        assertEquals(2, found.size());
    }

    @Test
    @DisplayName("10. clear() empties all events from the repository")
    void clearEmptiesRepository() {
        repository.add(SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "A"));
        assertEquals(1, repository.size());

        repository.clear();

        assertEquals(0, repository.size());
        assertTrue(repository.isEmpty());
    }

    @Test
    @DisplayName("11. Null arguments throw NullPointerException")
    void nullArgumentValidation() {
        assertThrows(NullPointerException.class, () -> repository.add(null));
        assertThrows(NullPointerException.class, () -> repository.addAll(null));
        assertThrows(NullPointerException.class, () -> repository.findBySeverity(null));
        assertThrows(NullPointerException.class, () -> repository.findByType(null));
        assertThrows(NullPointerException.class, () -> repository.findByPath(null));
    }
}

