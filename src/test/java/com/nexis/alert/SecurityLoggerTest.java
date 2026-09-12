package com.nexis.alert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SecurityLogger}.
 *
 * <p>All tests use a {@link TempDir} to avoid touching the real {@code logs/} directory
 * and to ensure test isolation and cleanup.
 */
class SecurityLoggerTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // 1. Creates log file when absent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1. log() creates the log file if it does not exist")
    void logCreatesFileWhenAbsent() throws IOException {
        Path logFile = tempDir.resolve("nexis.log");
        SecurityLogger logger = new SecurityLogger(logFile);

        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_CREATED, Severity.INFO,
            tempDir.resolve("newfile.txt"), "File creation detected.");

        logger.log(event);

        assertTrue(Files.exists(logFile), "Log file should be created after first log() call");
    }

    // -------------------------------------------------------------------------
    // 2. Log line contains expected fields
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2. Log line contains timestamp, severity, event type, path, and details")
    void logLineContainsExpectedFields() throws IOException {
        Path logFile = tempDir.resolve("nexis.log");
        SecurityLogger logger = new SecurityLogger(logFile);

        Path targetFile = tempDir.resolve("important.txt");
        SecurityEvent event = SecurityEvent.of(
            EventType.INTEGRITY_VIOLATION, Severity.CRITICAL,
            targetFile, "SHA-256 hash differs from baseline.");

        logger.log(event);

        String content = Files.readString(logFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("CRITICAL"),            "Log should contain severity");
        assertTrue(content.contains("INTEGRITY_VIOLATION"), "Log should contain event type");
        assertTrue(content.contains("SHA-256 hash differs from baseline."), "Log should contain details");
        // The path portion should be present
        assertTrue(content.contains("important.txt"), "Log should contain file name");
        // Delimiter
        assertTrue(content.contains("|"), "Log should use pipe delimiter");
    }

    // -------------------------------------------------------------------------
    // 3. Appends - does not overwrite
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. Two log() calls produce two lines (append mode)")
    void logAppendsWithoutOverwriting() throws IOException {
        Path logFile = tempDir.resolve("nexis.log");
        SecurityLogger logger = new SecurityLogger(logFile);

        SecurityEvent e1 = SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO,
            tempDir.resolve("a.txt"), "Created.");
        SecurityEvent e2 = SecurityEvent.of(EventType.FILE_DELETED, Severity.WARNING,
            tempDir.resolve("b.txt"), "Deleted.");

        logger.log(e1);
        logger.log(e2);

        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "Two log() calls should produce exactly two lines");
    }

    // -------------------------------------------------------------------------
    // 4. Auto-creates parent directory
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4. log() creates parent directories if they do not exist")
    void logCreatesParentDirectories() throws IOException {
        Path nestedLog = tempDir.resolve("sub").resolve("deep").resolve("nexis.log");
        SecurityLogger logger = new SecurityLogger(nestedLog);

        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_MODIFIED, Severity.WARNING,
            tempDir.resolve("file.txt"), "Modified.");

        logger.log(event);

        assertTrue(Files.exists(nestedLog), "Log file should be created even in a nested directory");
    }

    // -------------------------------------------------------------------------
    // 5. Path-less event logs "N/A"
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("5. Path-less event logs 'N/A' for the path column")
    void pathlessEventLogsNA() throws IOException {
        Path logFile = tempDir.resolve("nexis.log");
        SecurityLogger logger = new SecurityLogger(logFile);

        SecurityEvent event = SecurityEvent.of(
            EventType.SYSTEM_ERROR, Severity.ERROR, "Unexpected failure.");

        logger.log(event);

        String content = Files.readString(logFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("N/A"), "Path-less events should log 'N/A' for path column");
    }

    // -------------------------------------------------------------------------
    // 6. Logging failure does not throw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6. log() does not throw when log path is a directory (simulated failure)")
    void logDoesNotThrowOnWriteFailure() throws IOException {
        // Create a DIRECTORY at the log path location - this will prevent file creation
        Path blockingDir = tempDir.resolve("nexis.log");
        Files.createDirectory(blockingDir);

        SecurityLogger logger = new SecurityLogger(blockingDir);

        SecurityEvent event = SecurityEvent.of(
            EventType.FILE_CREATED, Severity.INFO,
            tempDir.resolve("x.txt"), "Should not crash.");

        // Must not throw - failure should be swallowed with a stderr message
        logger.log(event);
        // If we reach here, the test passes
    }

    // -------------------------------------------------------------------------
    // 7. Null rejection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7. log(null) throws NullPointerException")
    void logRejectsNull() {
        SecurityLogger logger = new SecurityLogger(tempDir.resolve("nexis.log"));
        assertThrows(NullPointerException.class, () -> logger.log(null));
    }

    // -------------------------------------------------------------------------
    // 8. getLogPath() returns configured path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("8. getLogPath() returns the path provided at construction")
    void getLogPathReturnsConfiguredPath() {
        Path logFile = tempDir.resolve("nexis.log");
        SecurityLogger logger = new SecurityLogger(logFile);
        assertEquals(logFile, logger.getLogPath());
    }
}
