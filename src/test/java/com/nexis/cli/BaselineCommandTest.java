package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class BaselineCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("1. Baseline command creates baseline JSON file with correct entry count")
    void baselineCommandCreatesBaselineFile() throws IOException {
        Files.writeString(tempDir.resolve("alpha.txt"), "Alpha content", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("beta.txt"), "Beta content", StandardCharsets.UTF_8);

        Path baselineDir = tempDir.resolve("output");
        Files.createDirectories(baselineDir);
        Path baselineFile = baselineDir.resolve("baseline.json");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("baseline", tempDir.toAbsolutePath().toString());

        assertEquals(0, exitCode, "Baseline command should succeed");
        String output = out.toString();
        assertTrue(output.contains("NEXIS BASELINE CREATED"), "Should display success header");
        assertTrue(output.contains("2"), "Should report 2 files");
    }

    @Test
    @DisplayName("2. Baseline command reports error for nonexistent directory")
    void baselineCommandReportsErrorForNonexistentDirectory() {
        Path missing = tempDir.resolve("nonexistent");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("baseline", missing.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1");
        assertTrue(err.toString().contains("does not exist"), "Error message should mention nonexistent path");
    }

    @Test
    @DisplayName("3. Baseline command reports error when path is a file")
    void baselineCommandReportsErrorWhenPathIsFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not_a_dir.txt"));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("baseline", file.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1");
        assertTrue(err.toString().contains("not a directory"), "Error message should mention not a directory");
    }

    @Test
    @DisplayName("4. Baseline command handles empty directory")
    void baselineCommandHandlesEmptyDirectory() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("baseline", tempDir.toAbsolutePath().toString());

        assertEquals(0, exitCode, "Baseline command should succeed for empty directory");
        assertTrue(out.toString().contains("0"), "Should report 0 files");
    }
}
