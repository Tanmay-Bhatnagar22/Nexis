package com.nexis.alert;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AlertManager}.
 *
 * <p>A {@link StringWriter} backed {@link PrintWriter} is injected so output
 * can be inspected without touching {@code System.out}.
 */
class AlertManagerTest {

    private StringWriter output;
    private AlertManager alertManager;

    @BeforeEach
    void setUp() {
        output = new StringWriter();
        alertManager = new AlertManager(new PrintWriter(output, true));
    }

    // -------------------------------------------------------------------------
    // 1. Constructor guards
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1. Constructor rejects null PrintWriter")
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new AlertManager(null));
    }

    // -------------------------------------------------------------------------
    // 2. INFO event formatting
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2. INFO FILE_CREATED event contains expected tokens")
    void infoEventContainsExpectedTokens() {
        Path path = Path.of("watch", "newfile.txt");
        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_CREATED, Severity.INFO, path, "File creation detected.");

        alertManager.alert(event);

        String out = output.toString();
        assertTrue(out.contains("[INFO]"),       "Output should contain [INFO] tag");
        assertTrue(out.contains("FILE_CREATED"), "Output should contain event type");
        assertTrue(out.contains("Path:"),        "Output should contain 'Path:' label");
        assertTrue(out.contains("File creation detected."), "Output should contain details");
    }

    // -------------------------------------------------------------------------
    // 3. WARNING event formatting
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. WARNING FILE_MODIFIED event contains [WARNING] tag")
    void warningEventContainsTag() {
        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_MODIFIED, Severity.WARNING,
            Path.of("doc.txt"), "File modification detected.");

        alertManager.alert(event);

        assertTrue(output.toString().contains("[WARNING]"), "Output should contain [WARNING] tag");
    }

    // -------------------------------------------------------------------------
    // 4. CRITICAL event has high-severity prefix
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4. CRITICAL INTEGRITY_VIOLATION event has '!!' prominence prefix")
    void criticalEventHasExclamationPrefix() {
        SecurityEvent event = SecurityEvent.of(
            EventType.INTEGRITY_VIOLATION, Severity.CRITICAL,
            Path.of("config.xml"), "SHA-256 hash differs from baseline.");

        alertManager.alert(event);

        String out = output.toString();
        assertTrue(out.contains("!!"), "CRITICAL event should have '!!' prefix");
        assertTrue(out.contains("[CRITICAL]"), "Output should contain [CRITICAL] tag");
        assertTrue(out.contains("INTEGRITY_VIOLATION"), "Output should contain event type");
    }

    // -------------------------------------------------------------------------
    // 5. ERROR event has high-severity prefix
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("5. ERROR event has '!!' prominence prefix")
    void errorEventHasExclamationPrefix() {
        SecurityEvent event = SecurityEvent.of(
            EventType.MONITORING_ERROR, Severity.ERROR, "Watch loop failed.");

        alertManager.alert(event);

        String out = output.toString();
        assertTrue(out.contains("!!"), "ERROR event should have '!!' prefix");
        assertTrue(out.contains("[ERROR]"), "Output should contain [ERROR] tag");
    }

    // -------------------------------------------------------------------------
    // 6. Path-less event (N/A path)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6. Path-less event shows 'N/A' in output")
    void pathlessEventShowsNA() {
        SecurityEvent event = SecurityEvent.of(
            EventType.SYSTEM_ERROR, Severity.ERROR, "Unexpected failure.");

        alertManager.alert(event);

        assertTrue(output.toString().contains("N/A"), "Path-less event should show 'N/A' for path");
    }

    // -------------------------------------------------------------------------
    // 7. alert() rejects null
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7. alert(null) throws NullPointerException")
    void alertRejectsNull() {
        assertThrows(NullPointerException.class, () -> alertManager.alert(null));
    }

    // -------------------------------------------------------------------------
    // 8. Multiple events produce multiple lines
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("8. Two consecutive alert() calls produce two output lines")
    void twoAlertsProduceTwoLines() {
        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO,
            Path.of("a.txt"), "Created.");
        SecurityEvent e2 = SecurityEvent.of(EventType.FILE_DELETED, Severity.WARNING,
            Path.of("b.txt"), "Deleted.");

        alertManager.alert(e1);
        alertManager.alert(e2);

        String[] lines = output.toString().split(System.lineSeparator());
        assertTrue(lines.length >= 2, "Expected at least 2 output lines");
    }

    // -------------------------------------------------------------------------
    // 9. INFO event does NOT carry '!!' prefix
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("9. INFO event does not carry '!!' prefix")
    void infoEventHasNoExclamationPrefix() {
        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_CREATED, Severity.INFO, Path.of("x.txt"), "Created.");

        alertManager.alert(event);

        // The line should not start with "!!" (it starts with "   " instead)
        String firstLine = output.toString().lines().findFirst().orElse("");
        assertTrue(!firstLine.startsWith("!!"), "INFO event must not start with '!!'");
        assertDoesNotThrow(() -> alertManager.alert(event));
    }
}
