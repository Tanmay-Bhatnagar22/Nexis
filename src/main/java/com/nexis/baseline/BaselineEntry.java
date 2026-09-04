package com.nexis.baseline;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a single baseline entry containing a normalized file path
 * and its expected cryptographic SHA-256 hash.
 *
 * @param filePath normalized path of the monitored file
 * @param sha256   expected SHA-256 cryptographic hash (64 lowercase hexadecimal characters)
 */
public record BaselineEntry(Path filePath, String sha256) {

    public BaselineEntry {
        Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(sha256, "SHA-256 hash cannot be null");

        filePath = filePath.normalize();
        sha256 = sha256.toLowerCase();

        if (!sha256.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Invalid SHA-256 hash format: " + sha256);
        }
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getSha256() {
        return sha256;
    }
}

