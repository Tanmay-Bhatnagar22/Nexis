package com.nexis.baseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineStorageTest {

    @TempDir
    Path tempDir;

    private BaselineStorage storage;

    @BeforeEach
    void setUp() {
        storage = new BaselineStorage();
    }

    @Test
    @DisplayName("1. Serialized JSON stores paths with forward slashes")
    void pathsSerializedWithForwardSlashes() throws IOException {
        Path destination = tempDir.resolve("sub").resolve("deep").resolve("baseline.json");
        BaselineEntry entry = new BaselineEntry(
            Path.of("src", "main", "java", "com", "nexis", "Main.java"),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );

        storage.writeBaseline(destination, List.of(entry));

        assertTrue(Files.exists(destination), "Nested directories and baseline file should be created");
        String json = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(json.contains("src/main/java/com/nexis/Main.java"), "Path in JSON must use forward slash");
    }

    @Test
    @DisplayName("2. Null arguments to writeBaseline throw IllegalArgumentException")
    void nullArgumentsToWriteBaselineThrowException() {
        var exception1 = assertThrows(
            IllegalArgumentException.class,
            () -> storage.writeBaseline(null, List.of())
        );
        assertNotNull(exception1, "Exception must be thrown and caught");

        var exception2 = assertThrows(
            IllegalArgumentException.class,
            () -> storage.writeBaseline(tempDir.resolve("out.json"), null)
        );
        assertNotNull(exception2, "Exception must be thrown and caught");
    }

    @Test
    @DisplayName("3. Null source to readBaseline throws IllegalArgumentException")
    void nullSourceToReadBaselineThrowsException() {
        var exception = assertThrows(
            IllegalArgumentException.class,
            () -> storage.readBaseline(null)
        );
        assertNotNull(exception, "Exception must be thrown and caught");
    }

    @Test
    @DisplayName("4. Reading a directory as baseline file throws BaselineStorageException")
    void readingDirectoryThrowsBaselineStorageException() {
        var exception = assertThrows(
            BaselineStorageException.class,
            () -> storage.readBaseline(tempDir)
        );
        assertNotNull(exception, "Exception must be thrown and caught");
    }

    @Test
    @DisplayName("5. Read baseline handles multiple sorted entries correctly")
    void readBaselineHandlesMultipleSortedEntries() throws IOException {
        Path destination = tempDir.resolve("baseline.json");
        BaselineEntry entryB = new BaselineEntry(
            Path.of("b.txt"),
            "1111111111111111111111111111111111111111111111111111111111111111"
        );
        BaselineEntry entryA = new BaselineEntry(
            Path.of("a.txt"),
            "2222222222222222222222222222222222222222222222222222222222222222"
        );

        // Write in reverse order
        storage.writeBaseline(destination, List.of(entryB, entryA));

        List<BaselineEntry> loaded = storage.readBaseline(destination);
        assertNotNull(loaded);
        assertEquals(2, loaded.size());
        assertEquals(Path.of("a.txt"), loaded.get(0).filePath(), "Entries should be sorted by path");
        assertEquals(Path.of("b.txt"), loaded.get(1).filePath());
    }
}

