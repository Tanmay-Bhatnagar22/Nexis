package com.nexis.report;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
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

class ReportGeneratorTest {

    private ReportGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ReportGenerator();
    }

    @Test
    @DisplayName("1. Empty report cleanly displays 'No security events recorded.'")
    void emptyReport() {
        String report = generator.generate(Collections.emptyList());

        assertTrue(report.contains("NEXIS SECURITY REPORT"));
        assertTrue(report.contains("No security events recorded."));
        assertFalse(report.contains("Total Events"));
        assertFalse(report.contains("CRITICAL EVENTS"));
    }

    @Test
    @DisplayName("2. Single event report displays correct summary, breakdown, and detail")
    void singleEventReport() {
        Path path = Path.of("test.txt");
        SecurityEvent event = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, path, "Created new file");

        String report = generator.generate(List.of(event));

        assertTrue(report.contains("Total Events       : 1"));
        assertTrue(report.contains("Informational      : 1"));
        assertTrue(report.contains("Critical Events    : 0"));
        assertTrue(report.contains("FILE_CREATED          1"));
        assertTrue(report.contains("EVENT DETAILS"));
        assertTrue(report.contains("Path: " + path.toAbsolutePath().normalize()));
        assertTrue(report.contains("Details: Created new file"));
    }

    @Test
    @DisplayName("3. Multiple events report displays correct aggregate counts")
    void multipleEventsReport() {
        List<SecurityEvent> events = List.of(
            SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("a.txt"), "A"),
            SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, Path.of("b.txt"), "B"),
            SecurityEvent.of(EventType.FILE_DELETED, Severity.WARNING, Path.of("c.txt"), "C"),
            SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("d.txt"), "D"),
            SecurityEvent.of(EventType.MONITORING_ERROR, Severity.ERROR, "E")
        );

        String report = generator.generate(events);

        assertTrue(report.contains("Total Events       : 5"));
        assertTrue(report.contains("Critical Events    : 1"));
        assertTrue(report.contains("Warnings           : 2"));
        assertTrue(report.contains("Errors             : 1"));
        assertTrue(report.contains("Informational      : 1"));

        assertTrue(report.contains("FILE_CREATED          1"));
        assertTrue(report.contains("FILE_MODIFIED         1"));
        assertTrue(report.contains("FILE_DELETED          1"));
        assertTrue(report.contains("INTEGRITY_VIOLATION   1"));
        assertTrue(report.contains("MONITORING_ERROR      1"));
    }

    @Test
    @DisplayName("4. Critical event extraction lists critical events under CRITICAL EVENTS section")
    void criticalEventExtraction() {
        Path criticalFile = Path.of("secret.key");
        SecurityEvent criticalEvent = SecurityEvent.of(
            EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, criticalFile, "SHA-256 hash mismatch");
        SecurityEvent infoEvent = SecurityEvent.of(
            EventType.FILE_CREATED, Severity.INFO, Path.of("log.txt"), "Log created");

        String report = generator.generate(List.of(infoEvent, criticalEvent));

        assertTrue(report.contains("CRITICAL EVENTS"));
        assertTrue(report.contains("INTEGRITY_VIOLATION"));
        assertTrue(report.contains("Path: " + criticalFile.toAbsolutePath().normalize()));
        assertTrue(report.contains("Details: SHA-256 hash mismatch"));
    }

    @Test
    @DisplayName("5. Correct path formatting handles events without file paths (N/A)")
    void pathFormattingHandlesNull() {
        SecurityEvent sysError = SecurityEvent.of(
            EventType.SYSTEM_ERROR, Severity.ERROR, "System disk failure");

        String report = generator.generate(List.of(sysError));

        assertTrue(report.contains("Path: N/A"));
        assertTrue(report.contains("Details: System disk failure"));
    }

    @Test
    @DisplayName("6. Writing to PrintWriter produces identical output")
    void printWriterOutputMatches() {
        SecurityEvent event = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("f.txt"), "F");
        String directString = generator.generate(List.of(event));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        generator.generate(List.of(event), pw);

        assertEquals(directString, sw.toString());
    }

    @Test
    @DisplayName("7. Null arguments throw NullPointerException")
    void nullArgumentsThrowException() {
        assertThrows(NullPointerException.class, () -> generator.generate(null));
        assertThrows(NullPointerException.class, () -> generator.generate(Collections.emptyList(), null));
    }
}

