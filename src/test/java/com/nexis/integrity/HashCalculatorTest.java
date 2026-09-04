package com.nexis.integrity;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashCalculatorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("1. Known-content file produces expected SHA-256 hash")
    void knownContentProducesExpectedSha256Hash() throws IOException {
        Path file = tempDir.resolve("fox.txt");
        String content = "The quick brown fox jumps over the lazy dog";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        String expectedHash = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592";
        String actualHash = HashCalculator.calculateSha256(file);

        assertEquals(expectedHash, actualHash, "SHA-256 hash must match known test vector");
    }

    @Test
    @DisplayName("2. Empty file produces known empty SHA-256 hash")
    void emptyFileProducesKnownEmptySha256Hash() throws IOException {
        Path emptyFile = Files.createFile(tempDir.resolve("empty.txt"));

        String expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String actualHash = HashCalculator.calculateSha256(emptyFile);

        assertEquals(expectedHash, actualHash, "Empty file hash must match standard SHA-256 empty digest");
    }

    @Test
    @DisplayName("3. Different contents produce different hashes")
    void differentContentsProduceDifferentHashes() throws IOException {
        Path fileA = tempDir.resolve("fileA.txt");
        Path fileB = tempDir.resolve("fileB.txt");

        Files.writeString(fileA, "Content version A", StandardCharsets.UTF_8);
        Files.writeString(fileB, "Content version B", StandardCharsets.UTF_8);

        String hashA = HashCalculator.calculateSha256(fileA);
        String hashB = HashCalculator.calculateSha256(fileB);

        assertNotEquals(hashA, hashB, "Different contents must produce different SHA-256 hashes");
    }

    @Test
    @DisplayName("4. Same contents produce the same hash")
    void sameContentsProduceIdenticalHash() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");

        String sharedContent = "Deterministic shared payload for Nexis integrity verification";
        Files.writeString(file1, sharedContent, StandardCharsets.UTF_8);
        Files.writeString(file2, sharedContent, StandardCharsets.UTF_8);

        String hash1 = HashCalculator.calculateSha256(file1);
        String hash2 = HashCalculator.calculateSha256(file2);

        assertEquals(hash1, hash2, "Identical contents across different files must produce identical hashes");
    }

    @Test
    @DisplayName("5. Missing file throws IllegalArgumentException")
    void missingFileThrowsIllegalArgumentException() {
        Path nonexistent = tempDir.resolve("missing_file.txt");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> HashCalculator.calculateSha256(nonexistent),
            "Hashing a nonexistent file must throw IllegalArgumentException"
        );

        assertTrue(
            exception.getMessage().contains("Target file does not exist"),
            "Exception message should mention that target file does not exist"
        );
    }

    @Test
    @DisplayName("6. Directory instead of file throws IllegalArgumentException")
    void directoryInsteadOfFileThrowsIllegalArgumentException() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("sub_directory"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> HashCalculator.calculateSha256(dir),
            "Hashing a directory must throw IllegalArgumentException"
        );

        assertTrue(
            exception.getMessage().contains("Target path is a directory"),
            "Exception message should indicate path is a directory"
        );
    }

    @Test
    @DisplayName("7. Large file streaming produces correct hash")
    void largeFileStreamingProducesCorrectHash() throws IOException {
        Path largeFile = tempDir.resolve("large_5mb.bin");
        int totalBytes = 5 * 1024 * 1024; // 5 MB
        byte[] chunk = new byte[64 * 1024]; // 64 KB chunk
        Arrays.fill(chunk, (byte) 'A');

        try (OutputStream os = Files.newOutputStream(largeFile);
             BufferedOutputStream bos = new BufferedOutputStream(os)) {
            int written = 0;
            while (written < totalBytes) {
                int bytesToWrite = Math.min(chunk.length, totalBytes - written);
                bos.write(chunk, 0, bytesToWrite);
                written += bytesToWrite;
            }
        }

        String expectedHash = "dbbe5517996826bd5861ac22b745d21d11219055d89243ca1aea0ad31f552b12";
        String actualHash = HashCalculator.calculateSha256(largeFile);

        assertEquals(expectedHash, actualHash, "Large file streaming must compute exact expected SHA-256 hash");
    }

    @Test
    @DisplayName("8. Null file path throws IllegalArgumentException")
    void nullFilePathThrowsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> HashCalculator.calculateSha256(null),
            "Passing null file path must throw IllegalArgumentException"
        );

        assertTrue(
            exception.getMessage().contains("Target file path cannot be null"),
            "Exception message should mention null target file path"
        );
    }

    @Test
    @DisplayName("9. Hash format is exactly 64 lowercase hexadecimal characters")
    void hashFormatIsLowercaseHexadecimal() throws IOException {
        Path file = tempDir.resolve("format_test.txt");
        Files.writeString(file, "Sample format check content", StandardCharsets.UTF_8);

        String hash = HashCalculator.calculateSha256(file);

        assertEquals(64, hash.length(), "SHA-256 hex string must be exactly 64 characters");
        assertTrue(hash.matches("^[0-9a-f]{64}$"), "Hash must contain only lowercase hexadecimal characters");
    }
}

