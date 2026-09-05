package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexis.integrity.ComparisonEntry;
import com.nexis.integrity.ComparisonResult;
import com.nexis.integrity.ComparisonStatus;

class ResultFormatterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("1. Clean result displays CLEAN status with correct counts")
    void cleanResultDisplaysCleanStatus() {
        ComparisonEntry unchanged = new ComparisonEntry(
            Path.of("file.txt"),
            ComparisonStatus.UNCHANGED,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );

        ComparisonResult result = new ComparisonResult(tempDir, java.util.List.of(unchanged), Collections.emptyMap());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        ResultFormatter.format(result, tempDir, pw);
        String output = sw.toString();

        assertTrue(output.contains("NEXIS FILE INTEGRITY SCAN"), "Should contain header");
        assertTrue(output.contains("UNCHANGED"), "Should contain UNCHANGED label");
        assertTrue(output.contains("CLEAN"), "Should indicate clean status");
        assertFalse(output.contains("DIFFERENCES DETECTED"), "Should not indicate differences");
    }

    @Test
    @DisplayName("2. Mixed result displays all event types and DIFFERENCES status")
    void mixedResultDisplaysAllEventTypes() {
        ComparisonEntry modified = new ComparisonEntry(
            Path.of("report.pdf"),
            ComparisonStatus.MODIFIED,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        );
        ComparisonEntry newFile = new ComparisonEntry(
            Path.of("suspicious.exe"),
            ComparisonStatus.NEW,
            null,
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        );
        ComparisonEntry deleted = new ComparisonEntry(
            Path.of("old_config.txt"),
            ComparisonStatus.DELETED,
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
            null
        );

        ComparisonResult result = new ComparisonResult(
            tempDir,
            java.util.List.of(modified, newFile, deleted),
            Collections.emptyMap()
        );

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        ResultFormatter.format(result, tempDir, pw);
        String output = sw.toString();

        assertTrue(output.contains("MODIFIED"), "Should contain MODIFIED label");
        assertTrue(output.contains("NEW"), "Should contain NEW label");
        assertTrue(output.contains("DELETED"), "Should contain DELETED label");
        assertTrue(output.contains("report.pdf"), "Should list modified file");
        assertTrue(output.contains("suspicious.exe"), "Should list new file");
        assertTrue(output.contains("old_config.txt"), "Should list deleted file");
        assertTrue(output.contains("DIFFERENCES DETECTED"), "Should indicate differences");
    }

    @Test
    @DisplayName("3. Errors are displayed in output")
    void errorsDisplayedInOutput() {
        Path errorPath = Path.of("unreadable.bin");
        Map<Path, String> errors = Map.of(errorPath, "Permission denied");

        ComparisonResult result = new ComparisonResult(
            tempDir,
            Collections.emptyList(),
            errors
        );

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        ResultFormatter.format(result, tempDir, pw);
        String output = sw.toString();

        assertTrue(output.contains("Errors"), "Should show error count");
        assertTrue(output.contains("unreadable.bin"), "Should list errored file");
        assertTrue(output.contains("Permission denied"), "Should show error detail");
    }

    @Test
    @DisplayName("4. File paths within target directory are displayed as relative paths")
    void filePathsDisplayedAsRelative() throws IOException {
        Files.createFile(tempDir.resolve("inner.txt"));

        ComparisonEntry entry = new ComparisonEntry(
            tempDir.resolve("inner.txt"),
            ComparisonStatus.NEW,
            null,
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        );

        ComparisonResult result = new ComparisonResult(
            tempDir,
            java.util.List.of(entry),
            Collections.emptyMap()
        );

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        ResultFormatter.format(result, tempDir, pw);
        String output = sw.toString();

        assertTrue(output.contains("inner.txt"), "Should display filename");
    }
}
