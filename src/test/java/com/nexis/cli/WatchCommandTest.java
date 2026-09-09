package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexis.alert.AlertManager;
import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.SecurityLogger;
import com.nexis.alert.Severity;
import com.nexis.baseline.BaselineManager;
import com.nexis.monitor.MonitorEvent;
import com.nexis.monitor.MonitorEventType;
import com.nexis.report.EventRepository;

import picocli.CommandLine;

/**
 * Unit and integration tests for {@link WatchCommand}.
 *
 * <p>Validates path validation and real-time event dispatching with baseline
 * comparison and integrity violation detection in an isolated temporary workspace.
 */
class WatchCommandTest {

    @TempDir
    Path tempDir;

    private Path monitoredDir;
    private Path workspaceDir;
    private Path baselineFile;
    private Path logFile;
    private Path eventsFile;
    private AlertManager alertManager;
    private SecurityLogger securityLogger;
    private EventRepository eventRepository;
    private BaselineManager baselineManager;

    @BeforeEach
    void setUp() throws IOException {
        monitoredDir = Files.createDirectory(tempDir.resolve("monitored"));
        workspaceDir = Files.createDirectory(tempDir.resolve("workspace"));
        baselineFile = workspaceDir.resolve("data").resolve("baseline.json");
        logFile = workspaceDir.resolve("logs").resolve("nexis.log");
        eventsFile = workspaceDir.resolve("data").resolve("events.json");

        alertManager = new AlertManager(new PrintWriter(new StringWriter()));
        securityLogger = new SecurityLogger(logFile);
        eventRepository = new EventRepository(eventsFile);
        baselineManager = new BaselineManager(baselineFile);
    }

    @Test
    @DisplayName("1. watch with nonexistent directory reports error and exits with code 1")
    void watchWithNonexistentDirectoryReportsError() {
        Path missing = tempDir.resolve("nonexistent");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI(workspaceDir);
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("watch", missing.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1 for nonexistent path");
        assertTrue(err.toString().contains("does not exist"),
            "Error message should mention that the path does not exist");
    }

    @Test
    @DisplayName("2. watch with a file path (not a directory) reports error and exits with code 1")
    void watchWithFilePathReportsError() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not_a_dir.txt"));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI(workspaceDir);
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("watch", file.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1 for a file path");
        assertTrue(err.toString().contains("not a directory"),
            "Error message should mention that the path is not a directory");
    }

    @Test
    @DisplayName("3. Modified file with different content triggers INTEGRITY_VIOLATION with CRITICAL severity and persists event")
    void modifiedFileWithDifferentContentTriggersIntegrityViolation() throws IOException {
        Path file = monitoredDir.resolve("important.txt");
        Files.writeString(file, "Original content", StandardCharsets.UTF_8);
        baselineManager.addOrUpdateFile(file);
        baselineManager.save();

        // Modify file with tampered content
        Files.writeString(file, "Tampered content", StandardCharsets.UTF_8);

        WatchCommand watch = new WatchCommand(baselineManager, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(file, MonitorEventType.MODIFIED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.INTEGRITY_VIOLATION, event.getEventType());
        assertEquals(Severity.CRITICAL, event.getSeverity());
        assertEquals(file.toAbsolutePath().normalize(), event.getFilePath());
        assertTrue(event.getDetails().contains("SHA-256 hash differs from baseline — possible tampering detected."));
        assertTrue(event.getDetails().contains("Expected:"));
        assertTrue(event.getDetails().contains("Found:"));

        // Verify persisted to in-memory repository
        assertEquals(1, eventRepository.getAll().size());
        assertEquals(EventType.INTEGRITY_VIOLATION, eventRepository.getAll().get(0).getEventType());

        // Verify saved to disk file
        EventRepository reloaded = EventRepository.loadOrDefault(eventsFile);
        assertEquals(1, reloaded.getAll().size());
        assertEquals(EventType.INTEGRITY_VIOLATION, reloaded.getAll().get(0).getEventType());
        assertEquals(Severity.CRITICAL, reloaded.getAll().get(0).getSeverity());
    }

    @Test
    @DisplayName("4. File modified but hash unchanged triggers FILE_MODIFIED with WARNING severity and no integrity violation")
    void modifiedFileWithUnchangedHashTriggersFileModified() throws IOException {
        Path file = monitoredDir.resolve("config.txt");
        Files.writeString(file, "Config line 1", StandardCharsets.UTF_8);
        baselineManager.addOrUpdateFile(file);
        baselineManager.save();

        // Rewrite with exact same content (touch mtime)
        Files.writeString(file, "Config line 1", StandardCharsets.UTF_8);

        WatchCommand watch = new WatchCommand(baselineManager, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(file, MonitorEventType.MODIFIED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.FILE_MODIFIED, event.getEventType());
        assertEquals(Severity.WARNING, event.getSeverity());
        assertEquals(file.toAbsolutePath().normalize(), event.getFilePath());
        assertTrue(event.getDetails().contains("hash unchanged"));
        assertFalse(event.getDetails().contains("INTEGRITY_VIOLATION"));

        assertEquals(1, eventRepository.getAll().size());
        assertEquals(EventType.FILE_MODIFIED, eventRepository.getAll().get(0).getEventType());
    }

    @Test
    @DisplayName("5. Deleted file triggers FILE_DELETED with WARNING severity")
    void deletedFileTriggersFileDeleted() throws IOException {
        Path file = monitoredDir.resolve("removed.txt");
        Files.writeString(file, "Temporary data", StandardCharsets.UTF_8);
        Files.delete(file);

        WatchCommand watch = new WatchCommand(baselineManager, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(file, MonitorEventType.DELETED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.FILE_DELETED, event.getEventType());
        assertEquals(Severity.WARNING, event.getSeverity());
        assertEquals(file.toAbsolutePath().normalize(), event.getFilePath());
        assertTrue(event.getDetails().contains("File deletion detected."));

        assertEquals(1, eventRepository.getAll().size());
        assertEquals(EventType.FILE_DELETED, eventRepository.getAll().get(0).getEventType());
    }

    @Test
    @DisplayName("6. Newly created file triggers FILE_CREATED with INFO severity")
    void createdFileTriggersFileCreated() throws IOException {
        Path file = monitoredDir.resolve("created.txt");
        Files.writeString(file, "Brand new file", StandardCharsets.UTF_8);

        WatchCommand watch = new WatchCommand(baselineManager, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(file, MonitorEventType.CREATED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.FILE_CREATED, event.getEventType());
        assertEquals(Severity.INFO, event.getSeverity());
        assertEquals(file.toAbsolutePath().normalize(), event.getFilePath());
        assertTrue(event.getDetails().contains("File creation detected."));
        assertTrue(event.getDetails().contains("SHA-256:"));

        assertEquals(1, eventRepository.getAll().size());
        assertEquals(EventType.FILE_CREATED, eventRepository.getAll().get(0).getEventType());
    }

    @Test
    @DisplayName("7. Missing or unreadable file on MODIFIED event is handled gracefully without exception")
    void missingFileOnModifiedEventHandledGracefully() {
        Path missing = monitoredDir.resolve("vanished.txt");

        WatchCommand watch = new WatchCommand(baselineManager, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(missing, MonitorEventType.MODIFIED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.FILE_MODIFIED, event.getEventType());
        assertEquals(Severity.WARNING, event.getSeverity());
        assertEquals(missing.toAbsolutePath().normalize(), event.getFilePath());
        assertTrue(event.getDetails().contains("File modification detected."));

        assertEquals(1, eventRepository.getAll().size());
    }

    @Test
    @DisplayName("8. Modified file not in baseline reports FILE_MODIFIED with WARNING")
    void unbaselinedFileModifiedReportsFileModified() throws IOException {
        Path file = monitoredDir.resolve("untracked.txt");
        Files.writeString(file, "Untracked content", StandardCharsets.UTF_8);

        WatchCommand watch = new WatchCommand(baselineManager, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(file, MonitorEventType.MODIFIED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.FILE_MODIFIED, event.getEventType());
        assertEquals(Severity.WARNING, event.getSeverity());
        assertEquals(file.toAbsolutePath().normalize(), event.getFilePath());
        assertTrue(event.getDetails().contains("File modification detected. SHA-256:"));

        assertEquals(1, eventRepository.getAll().size());
    }

    @Test
    @DisplayName("9. WatchCommand with baselinePath loads baseline and detects INTEGRITY_VIOLATION")
    void watchCommandWithBaselinePathDetectsIntegrityViolation() throws IOException {
        Path file = monitoredDir.resolve("secure.txt");
        Files.writeString(file, "Initial secure content", StandardCharsets.UTF_8);
        baselineManager.addOrUpdateFile(file);
        baselineManager.save();

        Files.writeString(file, "Tampered secure content", StandardCharsets.UTF_8);

        WatchCommand watch = new WatchCommand(baselineFile, logFile, eventsFile);
        MonitorEvent monitorEvent = new MonitorEvent(file, MonitorEventType.MODIFIED);

        SecurityEvent event = watch.dispatchEvent(monitorEvent, alertManager, securityLogger, eventRepository);

        assertNotNull(event);
        assertEquals(EventType.INTEGRITY_VIOLATION, event.getEventType());
        assertEquals(Severity.CRITICAL, event.getSeverity());
    }
}
