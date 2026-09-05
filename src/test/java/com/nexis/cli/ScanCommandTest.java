package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexis.baseline.BaselineManager;
import com.nexis.baseline.BaselineStorage;
import com.nexis.scanner.FileScanner;

import picocli.CommandLine;

class ScanCommandTest {

    @TempDir
    Path tempDir;

    private Path monitoredDir;

    @BeforeEach
    void setUp() throws IOException {
        monitoredDir = Files.createDirectory(tempDir.resolve("monitored"));
    }

    /**
     * Creates a baseline for the monitored directory by scanning and saving
     * to the standard default location (data/baseline.json relative to CWD).
     * Since tests run from the project root, we need to save to a known location
     * that the default BaselineManager will find.
     */
    private void createBaseline() throws IOException {
        FileScanner scanner = new FileScanner();
        var files = scanner.scan(monitoredDir);
        BaselineManager manager = new BaselineManager();
        manager.addOrUpdateFiles(files);
        manager.save();
    }

    private int runScan(StringWriter out, StringWriter err) {
        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute("scan", monitoredDir.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("1. Scan with unchanged files returns exit code 0 and reports CLEAN")
    void scanUnchangedFilesReportsClean() throws IOException {
        Files.writeString(monitoredDir.resolve("stable.txt"), "Stable content", StandardCharsets.UTF_8);
        createBaseline();

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runScan(out, err);

        assertEquals(0, exitCode, "Clean scan should return exit code 0");
        String output = out.toString();
        assertTrue(output.contains("CLEAN"), "Should report CLEAN status");
        assertTrue(output.contains("UNCHANGED"), "Should contain UNCHANGED label");
    }

    @Test
    @DisplayName("2. Modified file is detected and reported")
    void modifiedFileIsDetected() throws IOException {
        Path file = monitoredDir.resolve("changeable.txt");
        Files.writeString(file, "Original content", StandardCharsets.UTF_8);
        createBaseline();

        // Modify the file
        Files.writeString(file, "Tampered content", StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runScan(out, err);

        assertEquals(1, exitCode, "Scan with differences should return exit code 1");
        String output = out.toString();
        assertTrue(output.contains("MODIFIED"), "Should contain MODIFIED label");
        assertTrue(output.contains("changeable.txt"), "Should list the modified file");
        assertTrue(output.contains("DIFFERENCES DETECTED"), "Should report differences");
    }

    @Test
    @DisplayName("3. New file is detected and reported")
    void newFileIsDetected() throws IOException {
        Files.writeString(monitoredDir.resolve("existing.txt"), "Existing content", StandardCharsets.UTF_8);
        createBaseline();

        // Add a new file
        Files.writeString(monitoredDir.resolve("intruder.txt"), "Intruder content", StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runScan(out, err);

        assertEquals(1, exitCode, "Scan with new files should return exit code 1");
        String output = out.toString();
        assertTrue(output.contains("NEW"), "Should contain NEW label");
        assertTrue(output.contains("intruder.txt"), "Should list the new file");
    }

    @Test
    @DisplayName("4. Deleted file is detected and reported")
    void deletedFileIsDetected() throws IOException {
        Path file = monitoredDir.resolve("ephemeral.txt");
        Files.writeString(file, "Ephemeral content", StandardCharsets.UTF_8);
        createBaseline();

        // Delete the file
        Files.delete(file);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runScan(out, err);

        assertEquals(1, exitCode, "Scan with deleted files should return exit code 1");
        String output = out.toString();
        assertTrue(output.contains("DELETED"), "Should contain DELETED label");
        assertTrue(output.contains("ephemeral.txt"), "Should list the deleted file");
    }

    @Test
    @DisplayName("5. Mixed changes: MODIFIED + NEW + DELETED in a single scan")
    void mixedChangesDetectedInSingleScan() throws IOException {
        Path unchanged = monitoredDir.resolve("unchanged.txt");
        Path modified = monitoredDir.resolve("modified.txt");
        Path deleted = monitoredDir.resolve("deleted.txt");

        Files.writeString(unchanged, "Unchanged content", StandardCharsets.UTF_8);
        Files.writeString(modified, "Original modified content", StandardCharsets.UTF_8);
        Files.writeString(deleted, "To be deleted", StandardCharsets.UTF_8);

        createBaseline();

        // Apply changes
        Files.writeString(modified, "TAMPERED content", StandardCharsets.UTF_8);
        Files.delete(deleted);
        Files.writeString(monitoredDir.resolve("new_file.txt"), "New content", StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runScan(out, err);

        assertEquals(1, exitCode, "Scan with differences should return exit code 1");
        String output = out.toString();
        assertTrue(output.contains("UNCHANGED"), "Should contain UNCHANGED label");
        assertTrue(output.contains("MODIFIED"), "Should contain MODIFIED label");
        assertTrue(output.contains("NEW"), "Should contain NEW label");
        assertTrue(output.contains("DELETED"), "Should contain DELETED label");
    }

    @Test
    @DisplayName("6. Scan without existing baseline reports error")
    void scanWithoutBaselineReportsError() throws IOException {
        // Delete the baseline if it exists from a previous test
        Path defaultBaseline = Path.of("data", "baseline.json");
        Files.deleteIfExists(defaultBaseline);

        Files.writeString(monitoredDir.resolve("file.txt"), "Content", StandardCharsets.UTF_8);

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = runScan(out, err);

        assertEquals(1, exitCode, "Scan without baseline should return exit code 1");
        assertTrue(err.toString().contains("baseline"), "Error should mention baseline");
    }

    @Test
    @DisplayName("7. Scan with nonexistent directory reports error")
    void scanWithNonexistentDirectoryReportsError() {
        Path missing = tempDir.resolve("nonexistent");

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("scan", missing.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1");
        assertTrue(err.toString().contains("does not exist"), "Error should mention nonexistent path");
    }

    @Test
    @DisplayName("8. Scan with path pointing to file reports error")
    void scanWithFilePathReportsError() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not_a_dir.txt"));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI();
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("scan", file.toAbsolutePath().toString());

        assertEquals(1, exitCode, "Should fail with exit code 1");
        assertTrue(err.toString().contains("not a directory"), "Error should mention not a directory");
    }
}
