package com.nexis.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScannerTest {

    @TempDir
    Path tempDir;

    private final FileScanner scanner = new FileScanner();

    @Test
    @DisplayName("1. Scanning an empty directory returns an empty list")
    void scanEmptyDirectoryReturnsEmptyList() throws IOException {
        List<Path> files = scanner.scan(tempDir);
        assertTrue(files.isEmpty(), "Scanning an empty directory should return an empty list");
    }

    @Test
    @DisplayName("2. Scanning a directory containing files finds those files")
    void scanDirectoryContainingFilesFindsFiles() throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("file1.txt"));
        Path file2 = Files.createFile(tempDir.resolve("file2.txt"));

        List<Path> files = scanner.scan(tempDir);

        assertEquals(2, files.size(), "Should discover exactly two files");
        assertTrue(files.contains(file1), "Should contain file1.txt");
        assertTrue(files.contains(file2), "Should contain file2.txt");
    }

    @Test
    @DisplayName("3. Scanning nested directories finds files recursively")
    void scanNestedDirectoriesFindsFilesRecursively() throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("file1.txt"));
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Path file2 = Files.createFile(subDir.resolve("file2.txt"));
        Path deepDir = Files.createDirectories(subDir.resolve("deep"));
        Path file3 = Files.createFile(deepDir.resolve("file3.txt"));

        List<Path> files = scanner.scan(tempDir);

        assertEquals(3, files.size(), "Should discover all three nested files");
        assertTrue(files.contains(file1));
        assertTrue(files.contains(file2));
        assertTrue(files.contains(file3));
    }

    @Test
    @DisplayName("4. Directories themselves are not returned as files")
    void directoriesThemselvesAreNotReturnedAsFiles() throws IOException {
        Path dir1 = Files.createDirectory(tempDir.resolve("dir1"));
        Path dir2 = Files.createDirectory(dir1.resolve("dir2"));
        Path file = Files.createFile(dir2.resolve("regular.txt"));

        List<Path> files = scanner.scan(tempDir);

        assertEquals(1, files.size(), "Only regular files should be returned");
        assertEquals(file, files.get(0));
        assertFalse(files.contains(tempDir), "Root directory should not be in results");
        assertFalse(files.contains(dir1), "Subdirectory dir1 should not be in results");
        assertFalse(files.contains(dir2), "Subdirectory dir2 should not be in results");
        assertTrue(files.stream().allMatch(Files::isRegularFile), "All returned paths must be regular files");
    }

    @Test
    @DisplayName("5. A nonexistent path is handled correctly")
    void nonexistentPathThrowsInformativeException() {
        Path nonexistent = tempDir.resolve("does_not_exist");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> scanner.scan(nonexistent),
            "Scanning a nonexistent path should throw IllegalArgumentException"
        );

        assertTrue(
            exception.getMessage().contains("Scan path does not exist"),
            "Exception message should mention that scan path does not exist"
        );
    }

    @Test
    @DisplayName("6. A path pointing to a regular file instead of a directory is handled correctly")
    void regularFilePathThrowsInformativeException() throws IOException {
        Path regularFile = Files.createFile(tempDir.resolve("single_file.txt"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> scanner.scan(regularFile),
            "Scanning a regular file should throw IllegalArgumentException"
        );

        assertTrue(
            exception.getMessage().contains("Scan path is not a directory"),
            "Exception message should mention that scan path is not a directory"
        );
    }

    @Test
    @DisplayName("7. Results are deterministic and sorted")
    void resultsAreDeterministicAndSorted() throws IOException {
        Files.createFile(tempDir.resolve("z_file.txt"));
        Files.createFile(tempDir.resolve("a_file.txt"));
        Files.createFile(tempDir.resolve("m_file.txt"));
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        Files.createFile(subDir.resolve("b_file.txt"));

        List<Path> firstScan = scanner.scan(tempDir);
        List<Path> secondScan = scanner.scan(tempDir);

        assertEquals(firstScan, secondScan, "Multiple scans should return identical lists");

        List<Path> sortedExpected = firstScan.stream().sorted().toList();
        assertEquals(sortedExpected, firstScan, "Results should be sorted in predictable order");
    }

    @Test
    @DisplayName("8. Null path throws IllegalArgumentException")
    void nullPathThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> scanner.scan(null),
            "Scanning a null path should throw IllegalArgumentException"
        );

        assertTrue(
            exception.getMessage().contains("Scan path cannot be null"),
            "Exception message should mention null path"
        );
    }
}

