package com.nexis.alert;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SecurityEvent}.
 */
class SecurityEventTest {

    // -------------------------------------------------------------------------
    // 1. Factory with path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1. of(type, severity, path, details) creates event with correct fields")
    void factoryWithPathCreatesCorrectEvent() {
        Path path = Path.of("some", "file.txt");
        Instant before = Instant.now();

        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_MODIFIED, Severity.WARNING, path, "File was modified.");

        Instant after = Instant.now();

        assertEquals(EventType.FILE_MODIFIED, event.getEventType());
        assertEquals(Severity.WARNING, event.getSeverity());
        assertNotNull(event.getFilePath(), "filePath should not be null");
        assertEquals("File was modified.", event.getDetails());
        assertNotNull(event.getTimestamp());
        // Timestamp must be within the before/after window
        assertTrue(!event.getTimestamp().isBefore(before) && !event.getTimestamp().isAfter(after),
            "Timestamp should be between before and after Instant.now() calls");
    }

    @Test
    @DisplayName("2. filePath is stored as absolute normalized path")
    void filePathIsNormalized() {
        Path relative = Path.of("foo", "bar.txt");
        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_CREATED, Severity.INFO, relative, "Created.");

        assertTrue(event.getFilePath().isAbsolute(), "Stored path must be absolute");
    }

    // -------------------------------------------------------------------------
    // 2. Factory without path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. of(type, severity, details) creates event with null filePath")
    void factoryWithoutPathHasNullPath() {
        SecurityEvent event = SecurityEvent.of(
            EventType.SYSTEM_ERROR, Severity.ERROR, "Unexpected I/O failure.");

        assertNull(event.getFilePath(), "filePath should be null for path-less events");
        assertEquals(EventType.SYSTEM_ERROR, event.getEventType());
        assertEquals(Severity.ERROR, event.getSeverity());
        assertEquals("Unexpected I/O failure.", event.getDetails());
    }

    // -------------------------------------------------------------------------
    // 3. Null argument guards
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4. of(type, severity, path, details) rejects null eventType")
    void rejectsNullEventType() {
        assertThrows(NullPointerException.class,
            () -> SecurityEvent.of(null, Severity.INFO, Path.of("x"), "details"));
    }

    @Test
    @DisplayName("5. of(type, severity, path, details) rejects null severity")
    void rejectsNullSeverity() {
        assertThrows(NullPointerException.class,
            () -> SecurityEvent.of(EventType.FILE_CREATED, null, Path.of("x"), "details"));
    }

    @Test
    @DisplayName("6. of(type, severity, path, details) rejects null filePath")
    void rejectsNullFilePath() {
        assertThrows(NullPointerException.class,
            () -> SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, (Path) null, "details"));
    }

    @Test
    @DisplayName("7. of(type, severity, path, details) rejects null details")
    void rejectsNullDetails() {
        assertThrows(NullPointerException.class,
            () -> SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("x"), null));
    }

    @Test
    @DisplayName("8. of(type, severity, details) rejects null details")
    void rejectsNullDetailsNoPath() {
        assertThrows(NullPointerException.class,
            () -> SecurityEvent.of(EventType.SYSTEM_ERROR, Severity.ERROR, (String) null));
    }

    // -------------------------------------------------------------------------
    // 4. toString smoke test
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("9. toString() contains event type and severity")
    void toStringContainsKeyFields() {
        SecurityEvent event = SecurityEvent.of(
            EventType.INTEGRITY_VIOLATION, Severity.CRITICAL,
            Path.of("important.txt"), "Hash mismatch.");

        String s = event.toString();
        assertTrue(s.contains("INTEGRITY_VIOLATION"), "toString should contain event type");
        assertTrue(s.contains("CRITICAL"), "toString should contain severity");
    }

    // -------------------------------------------------------------------------
    // 5. Value equality and hashCode
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("10. Equal events compare equal and have identical hash codes")
    void equalEventsCompareEqualAndHaveMatchingHashCodes() {
        Instant ts = Instant.parse("2026-09-08T10:00:00Z");
        Path path = Path.of("audit.log");

        SecurityEvent e1 = SecurityEvent.of(ts, EventType.FILE_CREATED, Severity.INFO, path, "Created");
        SecurityEvent e2 = SecurityEvent.of(ts, EventType.FILE_CREATED, Severity.INFO, path, "Created");

        assertEquals(e1, e2, "Identical events must be equal");
        assertEquals(e2, e1, "Equality must be symmetric");
        assertEquals(e1.hashCode(), e2.hashCode(), "Equal events must have identical hash codes");
        assertEquals(e1, e1, "Equality must be reflexive");
    }

    @Test
    @DisplayName("11. Equal events without path compare equal and have identical hash codes")
    void equalEventsWithoutPathCompareEqual() {
        Instant ts = Instant.parse("2026-09-08T10:00:00Z");

        SecurityEvent e1 = SecurityEvent.of(ts, EventType.SYSTEM_ERROR, Severity.ERROR, null, "Failed");
        SecurityEvent e2 = SecurityEvent.of(ts, EventType.SYSTEM_ERROR, Severity.ERROR, null, "Failed");

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    @DisplayName("12. Differing fields produce inequality")
    void differingFieldsProduceInequality() {
        Instant ts1 = Instant.parse("2026-09-08T10:00:00Z");
        Instant ts2 = Instant.parse("2026-09-08T10:00:01Z");
        Path p1 = Path.of("file1.txt");
        Path p2 = Path.of("file2.txt");

        SecurityEvent base = SecurityEvent.of(ts1, EventType.FILE_CREATED, Severity.INFO, p1, "Details");

        // Differing timestamp
        SecurityEvent diffTs = SecurityEvent.of(ts2, EventType.FILE_CREATED, Severity.INFO, p1, "Details");
        assertNotEquals(base, diffTs);

        // Differing eventType
        SecurityEvent diffType = SecurityEvent.of(ts1, EventType.FILE_MODIFIED, Severity.INFO, p1, "Details");
        assertNotEquals(base, diffType);

        // Differing severity
        SecurityEvent diffSev = SecurityEvent.of(ts1, EventType.FILE_CREATED, Severity.WARNING, p1, "Details");
        assertNotEquals(base, diffSev);

        // Differing path
        SecurityEvent diffPath = SecurityEvent.of(ts1, EventType.FILE_CREATED, Severity.INFO, p2, "Details");
        assertNotEquals(base, diffPath);

        // Path vs null path
        SecurityEvent nullPath = SecurityEvent.of(ts1, EventType.FILE_CREATED, Severity.INFO, null, "Details");
        assertNotEquals(base, nullPath);
        assertNotEquals(nullPath, base);

        // Differing details
        SecurityEvent diffDet = SecurityEvent.of(ts1, EventType.FILE_CREATED, Severity.INFO, p1, "Other details");
        assertNotEquals(base, diffDet);
    }

    @Test
    @DisplayName("13. Comparison with null or different class returns false")
    void comparisonWithNullOrDifferentClassReturnsFalse() {
        SecurityEvent event = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "Details");

        assertNotEquals(null, event);
        assertNotEquals(event, null);
        assertNotEquals("string object", event);
        assertNotEquals(event, "string object");
    }
}
