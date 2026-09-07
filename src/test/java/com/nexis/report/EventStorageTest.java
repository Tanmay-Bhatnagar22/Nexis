package com.nexis.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;

class EventStorageTest {

    @TempDir
    Path tempDir;

    private EventStorage storage;
    private Path eventFile;

    @BeforeEach
    void setUp() {
        storage = new EventStorage();
        eventFile = tempDir.resolve("events.json");
    }

    @Test
    @DisplayName("1. Round-trip write and read preserves all event fields")
    void writeAndReadRoundTrip() throws IOException {
        Instant now = Instant.parse("2026-09-07T12:30:00Z");
        Path file = tempDir.resolve("monitored.txt");
        SecurityEvent event = SecurityEvent.of(now, EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, file, "Hash mismatch");

        storage.writeEvents(eventFile, List.of(event));

        assertTrue(Files.exists(eventFile));
        List<SecurityEvent> loaded = storage.readEvents(eventFile);

        assertEquals(1, loaded.size());
        SecurityEvent restored = loaded.get(0);
        assertEquals(now, restored.getTimestamp());
        assertEquals(EventType.INTEGRITY_VIOLATION, restored.getEventType());
        assertEquals(Severity.CRITICAL, restored.getSeverity());
        assertEquals(file.toAbsolutePath().normalize(), restored.getFilePath());
        assertEquals("Hash mismatch", restored.getDetails());
    }

    @Test
    @DisplayName("2. Reading nonexistent file returns empty list")
    void readNonexistentFileReturnsEmpty() throws IOException {
        Path missing = tempDir.resolve("missing.json");
        List<SecurityEvent> events = storage.readEvents(missing);
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("3. Writing and reading empty collection returns empty list")
    void writeAndReadEmptyList() throws IOException {
        storage.writeEvents(eventFile, Collections.emptyList());
        List<SecurityEvent> loaded = storage.readEvents(eventFile);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("4. Reading directory path throws IOException")
    void readDirectoryThrowsIOException() {
        assertThrows(IOException.class, () -> storage.readEvents(tempDir));
    }

    @Test
    @DisplayName("5. Null arguments throw IllegalArgumentException")
    void nullArgumentsThrowException() {
        assertThrows(IllegalArgumentException.class, () -> storage.writeEvents(null, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> storage.writeEvents(eventFile, null));
        assertThrows(IllegalArgumentException.class, () -> storage.readEvents(null));
    }

    @Test
    @DisplayName("6. Malformed JSON throws IOException")
    void malformedJsonThrowsIOException() throws IOException {
        Files.writeString(eventFile, "{ not valid json ");
        assertThrows(IOException.class, () -> storage.readEvents(eventFile));
    }

    @Test
    @DisplayName("7. Reading file with unsupported future schema version throws IOException")
    void readUnsupportedSchemaVersionThrowsIOException() throws IOException {
        String futureJson = """
            {
              "version": 999,
              "events": []
            }
            """;
        Files.writeString(eventFile, futureJson);
        IOException ex = assertThrows(IOException.class, () -> storage.readEvents(eventFile));
        assertTrue(ex.getMessage().contains("Unsupported event storage schema version: 999"));
    }
}

