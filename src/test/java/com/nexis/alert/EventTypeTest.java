package com.nexis.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EventType}.
 */
class EventTypeTest {

    @Test
    @DisplayName("1. All expected EventType constants are present")
    void allConstantsPresent() {
        EventType[] values = EventType.values();
        assertEquals(6, values.length, "Expected exactly 6 EventType constants");
    }

    @Test
    @DisplayName("2. FILE_CREATED constant exists and has correct name")
    void fileCreatedExists() {
        assertNotNull(EventType.FILE_CREATED);
        assertEquals("FILE_CREATED", EventType.FILE_CREATED.name());
    }

    @Test
    @DisplayName("3. FILE_MODIFIED constant exists")
    void fileModifiedExists() {
        assertNotNull(EventType.FILE_MODIFIED);
    }

    @Test
    @DisplayName("4. FILE_DELETED constant exists")
    void fileDeletedExists() {
        assertNotNull(EventType.FILE_DELETED);
    }

    @Test
    @DisplayName("5. INTEGRITY_VIOLATION constant exists")
    void integrityViolationExists() {
        assertNotNull(EventType.INTEGRITY_VIOLATION);
    }

    @Test
    @DisplayName("6. MONITORING_ERROR constant exists")
    void monitoringErrorExists() {
        assertNotNull(EventType.MONITORING_ERROR);
    }

    @Test
    @DisplayName("7. SYSTEM_ERROR constant exists")
    void systemErrorExists() {
        assertNotNull(EventType.SYSTEM_ERROR);
    }

    @Test
    @DisplayName("8. valueOf() works for all constants")
    void valueOfWorks() {
        for (EventType type : EventType.values()) {
            assertEquals(type, EventType.valueOf(type.name()));
        }
    }
}
