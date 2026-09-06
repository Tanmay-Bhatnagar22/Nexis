package com.nexis.alert;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
