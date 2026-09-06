package com.nexis.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Severity}.
 */
class SeverityTest {

    @Test
    @DisplayName("1. All expected Severity constants are present")
    void allConstantsPresent() {
        Severity[] values = Severity.values();
        assertEquals(4, values.length, "Expected exactly 4 Severity constants");
    }

    @Test
    @DisplayName("2. INFO constant exists and has correct name")
    void infoExists() {
        assertNotNull(Severity.INFO);
        assertEquals("INFO", Severity.INFO.name());
    }

    @Test
    @DisplayName("3. WARNING constant exists")
    void warningExists() {
        assertNotNull(Severity.WARNING);
    }

    @Test
    @DisplayName("4. CRITICAL constant exists")
    void criticalExists() {
        assertNotNull(Severity.CRITICAL);
    }

    @Test
    @DisplayName("5. ERROR constant exists")
    void errorExists() {
        assertNotNull(Severity.ERROR);
    }

    @Test
    @DisplayName("6. valueOf() works for all constants")
    void valueOfWorks() {
        for (Severity s : Severity.values()) {
            assertEquals(s, Severity.valueOf(s.name()));
        }
    }
}
