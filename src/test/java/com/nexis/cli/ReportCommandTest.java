package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;
import com.nexis.report.EventRepository;
import com.nexis.report.EventStorage;

import picocli.CommandLine;

class ReportCommandTest {

    private final Path defaultEventsPath = EventRepository.DEFAULT_EVENTS_PATH;

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(defaultEventsPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(defaultEventsPath);
    }

    private int runReport(StringWriter out, StringWriter err, String... args) {
        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "report";
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        return cmd.execute(fullArgs);
    }

    @Test
    @DisplayName("1. report with no stored events returns exit code 0 and empty report message")
    void reportNoEvents() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err);

        assertEquals(0, exitCode);
        assertTrue(out.toString().contains("No security events recorded."));
    }

    @Test
    @DisplayName("2. report with stored events returns exit code 0 and full summary")
    void reportWithEvents() throws IOException {
        EventStorage storage = new EventStorage();
        storage.writeEvents(defaultEventsPath, List.of(
            SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("created.txt"), "New file"),
            SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("tampered.txt"), "Tampered")
        ));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err);

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("Total Events       : 2"));
        assertTrue(output.contains("Critical Events    : 1"));
        assertTrue(output.contains("Informational      : 1"));
        assertTrue(output.contains("CRITICAL EVENTS"));
        assertTrue(output.contains("tampered.txt"));
    }

    @Test
    @DisplayName("3. report --severity filters events by severity")
    void reportFilterBySeverity() throws IOException {
        EventStorage storage = new EventStorage();
        storage.writeEvents(defaultEventsPath, List.of(
            SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("info.txt"), "Info"),
            SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, Path.of("crit.txt"), "Critical")
        ));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err, "--severity", "CRITICAL");

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("Total Events       : 1"));
        assertTrue(output.contains("Critical Events    : 1"));
        assertTrue(output.contains("crit.txt"));
        assertFalse(output.contains("info.txt"));
    }

    @Test
    @DisplayName("4. report --type filters events by event type")
    void reportFilterByType() throws IOException {
        EventStorage storage = new EventStorage();
        storage.writeEvents(defaultEventsPath, List.of(
            SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("created.txt"), "Created"),
            SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, Path.of("modified.txt"), "Modified")
        ));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err, "--type", "FILE_CREATED");

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("Total Events       : 1"));
        assertTrue(output.contains("created.txt"));
        assertFalse(output.contains("modified.txt"));
    }

    @Test
    @DisplayName("5. report --path filters events by affected file path")
    void reportFilterByPath() throws IOException {
        Path target = Path.of("specific.txt").toAbsolutePath().normalize();
        Path other = Path.of("other.txt").toAbsolutePath().normalize();

        EventStorage storage = new EventStorage();
        storage.writeEvents(defaultEventsPath, List.of(
            SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, target, "Created target"),
            SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, other, "Modified other")
        ));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err, "--path", target.toString());

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("Total Events       : 1"));
        assertTrue(output.contains(target.toString()));
        assertFalse(output.contains(other.toString()));
    }

    @Test
    @DisplayName("6. report with invalid --severity outputs error and exits with code 1")
    void reportInvalidSeverity() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err, "--severity", "SUPER_CRITICAL");

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("Invalid severity 'SUPER_CRITICAL'"));
    }

    @Test
    @DisplayName("7. report with invalid --type outputs error and exits with code 1")
    void reportInvalidType() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err, "--type", "UNKNOWN_EVENT_TYPE");

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("Invalid event type 'UNKNOWN_EVENT_TYPE'"));
    }

    @Test
    @DisplayName("8. report --clear clears stored events and returns exit code 0")
    void reportClearEvents() throws IOException {
        EventStorage storage = new EventStorage();
        storage.writeEvents(defaultEventsPath, List.of(
            SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, Path.of("f.txt"), "F")
        ));
        assertTrue(Files.exists(defaultEventsPath));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runReport(out, err, "--clear");

        assertEquals(0, exitCode);
        assertTrue(out.toString().contains("Security events cleared."));
        assertFalse(Files.exists(defaultEventsPath));
    }
}

