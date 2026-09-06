package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * CLI-layer tests for {@link WatchCommand}. These tests validate path validation
 * logic only — they do not start a long-running WatchService loop.
 */
class WatchCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("1. watch with nonexistent directory reports error and exits with code 1")
    void watchWithNonexistentDirectoryReportsError() {
        Path missing = tempDir.resolve("nonexistent");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
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

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("watch", file.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1 for a file path");
        assertTrue(err.toString().contains("not a directory"),
            "Error message should mention that the path is not a directory");
    }
}
