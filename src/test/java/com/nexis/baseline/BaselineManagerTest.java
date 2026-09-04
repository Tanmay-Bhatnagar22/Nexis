package com.nexis.baseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexis.integrity.HashCalculator;

class BaselineManagerTest {

    @TempDir
    Path tempDir;

    private Path baselineFile;
    private BaselineManager manager;

    @BeforeEach
    void setUp() {
        baselineFile = tempDir.resolve("baseline.json");
        manager = new BaselineManager(baselineFile);
    }

    @Test
    @DisplayName("1. Add file to baseline stores entry and reports existence")
    void addFileToBaseline() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "Sample content for baseline entry", StandardCharsets.UTF_8);

        BaselineEntry entry = manager.addOrUpdateFile(file);

        assertNotNull(entry, "Returned entry must not be null");
        assertEquals(file.normalize(), entry.filePath());
        assertTrue(manager.hasEntry(file), "Manager must report that the file entry exists");
        assertEquals(1, manager.getEntryCount(), "Baseline must contain exactly 1 entry");
    }

    @Test
    @DisplayName("2. Correct hash stored matches HashCalculator calculation")
    void correctHashStored() throws IOException {
        Path file = tempDir.resolve("hash_check.txt");
        Files.writeString(file, "Strict hash verification payload", StandardCharsets.UTF_8);

        String expectedHash = HashCalculator.calculateSha256(file);
        BaselineEntry entry = manager.addOrUpdateFile(file);

        assertEquals(expectedHash, entry.sha256(), "Stored hash must match HashCalculator output");

        Optional<BaselineEntry> retrieved = manager.getEntry(file);
        assertTrue(retrieved.isPresent(), "Retrieved entry must be present");
        assertEquals(expectedHash, retrieved.get().sha256(), "Retrieved entry hash must match expected");
    }

    @Test
    @DisplayName("3. Multiple files can be added and stored in baseline")
    void multipleFilesAddedToBaseline() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Path file3 = tempDir.resolve("file3.txt");

        Files.writeString(file1, "Content 1", StandardCharsets.UTF_8);
        Files.writeString(file2, "Content 2", StandardCharsets.UTF_8);
        Files.writeString(file3, "Content 3", StandardCharsets.UTF_8);

        List<BaselineEntry> entries = manager.addOrUpdateFiles(List.of(file1, file2, file3));

        assertEquals(3, entries.size(), "Batch add must return 3 entries");
        assertEquals(3, manager.getEntryCount(), "Manager must store 3 entries");
        assertTrue(manager.hasEntry(file1));
        assertTrue(manager.hasEntry(file2));
        assertTrue(manager.hasEntry(file3));
    }

    @Test
    @DisplayName("4. Update existing entry updates stored hash when file content changes")
    void updateExistingEntry() throws IOException {
        Path file = tempDir.resolve("modifiable.txt");
        Files.writeString(file, "Initial version", StandardCharsets.UTF_8);

        BaselineEntry initialEntry = manager.addOrUpdateFile(file);
        String initialHash = initialEntry.sha256();

        Files.writeString(file, "Modified version", StandardCharsets.UTF_8);
        BaselineEntry updatedEntry = manager.addOrUpdateFile(file);
        String updatedHash = updatedEntry.sha256();

        assertFalse(initialHash.equalsIgnoreCase(updatedHash), "Updated hash must differ from initial hash");
        assertEquals(1, manager.getEntryCount(), "Updating existing entry must not increase entry count");
        assertEquals(updatedHash, manager.getEntry(file).orElseThrow().sha256());
    }

    @Test
    @DisplayName("5. Remove entry deletes it from baseline")
    void removeEntry() throws IOException {
        Path file = tempDir.resolve("removable.txt");
        Files.writeString(file, "Temporary record", StandardCharsets.UTF_8);

        manager.addOrUpdateFile(file);
        assertTrue(manager.hasEntry(file));

        boolean removed = manager.removeEntry(file);
        assertTrue(removed, "removeEntry should return true for existing entry");
        assertFalse(manager.hasEntry(file), "File should no longer exist in baseline");
        assertEquals(0, manager.getEntryCount());

        boolean removedAgain = manager.removeEntry(file);
        assertFalse(removedAgain, "Removing non-existent entry should return false");
    }

    @Test
    @DisplayName("6. Save baseline persists JSON file successfully")
    void saveBaselineCreatesJsonFile() throws IOException {
        Path file = tempDir.resolve("target.txt");
        Files.writeString(file, "Save persistence content", StandardCharsets.UTF_8);

        manager.addOrUpdateFile(file);
        manager.save();

        assertTrue(Files.exists(baselineFile), "Baseline JSON file must be created on disk");
        String jsonContent = Files.readString(baselineFile, StandardCharsets.UTF_8);
        assertTrue(jsonContent.contains("target.txt"), "Saved JSON should contain the file name");
        assertTrue(jsonContent.contains(manager.getEntry(file).orElseThrow().sha256()), "Saved JSON should contain the hash");
    }

    @Test
    @DisplayName("7. Load baseline restores entries into new manager instance")
    void loadBaselineRestoresEntries() throws IOException {
        Path file1 = tempDir.resolve("restore1.txt");
        Path file2 = tempDir.resolve("restore2.txt");
        Files.writeString(file1, "Alpha payload", StandardCharsets.UTF_8);
        Files.writeString(file2, "Beta payload", StandardCharsets.UTF_8);

        manager.addOrUpdateFile(file1);
        manager.addOrUpdateFile(file2);
        manager.save();

        BaselineManager newManager = new BaselineManager(baselineFile);
        assertTrue(newManager.isEmpty(), "New manager should start empty before load");

        newManager.load();
        assertEquals(2, newManager.getEntryCount(), "Loaded manager must have 2 entries");
        assertTrue(newManager.hasEntry(file1));
        assertTrue(newManager.hasEntry(file2));
        assertEquals(manager.getEntry(file1).get().sha256(), newManager.getEntry(file1).get().sha256());
        assertEquals(manager.getEntry(file2).get().sha256(), newManager.getEntry(file2).get().sha256());
    }

    @Test
    @DisplayName("8. Missing baseline file throws BaselineStorageException")
    void missingBaselineFileThrowsCleanException() {
        Path nonexistentBaseline = tempDir.resolve("nonexistent_baseline.json");
        BaselineManager missingManager = new BaselineManager(nonexistentBaseline);

        BaselineStorageException exception = assertThrows(
            BaselineStorageException.class,
            missingManager::load,
            "Loading a nonexistent baseline file must throw BaselineStorageException"
        );

        assertTrue(
            exception.getMessage().contains("Baseline file does not exist"),
            "Exception message should mention that baseline file does not exist"
        );
    }

    @Test
    @DisplayName("9. Invalid JSON reports problem appropriately")
    void invalidJsonThrowsBaselineStorageException() throws IOException {
        Path corruptedBaseline = tempDir.resolve("corrupted.json");
        Files.writeString(corruptedBaseline, "{ this is not valid json : [[", StandardCharsets.UTF_8);

        BaselineManager corruptedManager = new BaselineManager(corruptedBaseline);

        BaselineStorageException exception = assertThrows(
            BaselineStorageException.class,
            corruptedManager::load,
            "Loading malformed JSON baseline must throw BaselineStorageException"
        );

        assertTrue(
            exception.getMessage().contains("Malformed baseline JSON"),
            "Exception message should indicate malformed baseline JSON"
        );
    }

    @Test
    @DisplayName("10. Empty baseline can be created, saved, and loaded cleanly")
    void emptyBaselineCanBeSavedAndLoaded() throws IOException {
        assertTrue(manager.isEmpty());
        manager.save();

        assertTrue(Files.exists(baselineFile), "Empty baseline JSON file should be created");

        BaselineManager newManager = new BaselineManager(baselineFile);
        newManager.load();
        assertTrue(newManager.isEmpty(), "Loaded baseline must be empty");
        assertEquals(0, newManager.getEntryCount());
    }

    @Test
    @DisplayName("11. Redundant path segments are normalized cleanly")
    void pathNormalizationHandlesRedundantSegments() throws IOException {
        Path base = Files.createDirectory(tempDir.resolve("nested"));
        Path file = Files.createFile(base.resolve("test.txt"));

        Path redundantPath = tempDir.resolve("nested").resolve(".").resolve("test.txt");
        manager.addOrUpdateFile(redundantPath);

        assertTrue(manager.hasEntry(file), "Querying with canonical path must match normalized stored path");
        assertTrue(manager.hasEntry(redundantPath), "Querying with redundant path must match normalized stored path");
    }

    @Test
    @DisplayName("12. Hashing failure on nonexistent or directory path throws clean exception")
    void hashingFailureThrowsCleanException() {
        Path missing = tempDir.resolve("ghost.txt");

        var exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.addOrUpdateFile(missing),
            "Adding a nonexistent file must fail cleanly via HashCalculator validation"
        );
        assertNotNull(exception, "Exception must be thrown and caught");
        assertEquals(0, manager.getEntryCount(), "Failed file addition must not alter baseline state");
    }

    @Test
    @DisplayName("13. 0-byte baseline file on disk loads cleanly as empty baseline")
    void zeroByteBaselineFileLoadsAsEmpty() throws IOException {
        Path emptyFile = Files.createFile(tempDir.resolve("empty_baseline.json"));
        BaselineManager emptyFileManager = new BaselineManager(emptyFile);

        emptyFileManager.load();
        assertTrue(emptyFileManager.isEmpty(), "A 0-byte baseline file should load as an empty baseline");
    }

    @Test
    @DisplayName("14. Malformed entry with invalid hash throws BaselineStorageException")
    void malformedEntryInJsonThrowsBaselineStorageException() throws IOException {
        Path malformedEntryFile = tempDir.resolve("invalid_entry.json");
        String badJson = """
            {
              "version": 1,
              "entries": [
                {
                  "filePath": "some/file.txt",
                  "sha256": "not-a-valid-64-char-hex"
                }
              ]
            }
            """;
        Files.writeString(malformedEntryFile, badJson, StandardCharsets.UTF_8);

        BaselineManager badManager = new BaselineManager(malformedEntryFile);
        var exception = assertThrows(
            BaselineStorageException.class,
            badManager::load,
            "Baseline with an invalid SHA-256 hash must throw BaselineStorageException"
        );
        assertNotNull(exception, "Exception must be thrown and caught");
    }
}

