package com.nexis.integrity;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the integrity comparison result for a single file.
 *
 * @param filePath     normalized path of the monitored file
 * @param status       integrity comparison status
 * @param baselineHash expected SHA-256 cryptographic hash from baseline, or null if file is newly discovered
 * @param currentHash  actual SHA-256 cryptographic hash of current disk content, or null if file is deleted
 */
public record ComparisonEntry(
    Path filePath,
    ComparisonStatus status,
    String baselineHash,
    String currentHash
) {

    public ComparisonEntry {
        Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(status, "Comparison status cannot be null");

        filePath = filePath.normalize();
        if (baselineHash != null) {
            baselineHash = baselineHash.toLowerCase();
        }
        if (currentHash != null) {
            currentHash = currentHash.toLowerCase();
        }
    }

    /**
     * Returns the normalized path of the file.
     *
     * @return normalized Path
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Returns the comparison status.
     *
     * @return ComparisonStatus
     */
    public ComparisonStatus getStatus() {
        return status;
    }

    /**
     * Returns an Optional containing the expected baseline hash, or empty Optional if absent.
     *
     * @return Optional of baseline SHA-256 hash
     */
    public Optional<String> getBaselineHash() {
        return Optional.ofNullable(baselineHash);
    }

    /**
     * Returns an Optional containing the actual current file hash, or empty Optional if absent.
     *
     * @return Optional of current SHA-256 hash
     */
    public Optional<String> getCurrentHash() {
        return Optional.ofNullable(currentHash);
    }

    /**
     * Indicates whether the file is unchanged.
     *
     * @return true if status is UNCHANGED
     */
    public boolean isUnchanged() {
        return status == ComparisonStatus.UNCHANGED;
    }

    /**
     * Indicates whether the file has been modified.
     *
     * @return true if status is MODIFIED
     */
    public boolean isModified() {
        return status == ComparisonStatus.MODIFIED;
    }

    /**
     * Indicates whether the file is newly discovered.
     *
     * @return true if status is NEW
     */
    public boolean isNew() {
        return status == ComparisonStatus.NEW;
    }

    /**
     * Indicates whether the file was deleted.
     *
     * @return true if status is DELETED
     */
    public boolean isDeleted() {
        return status == ComparisonStatus.DELETED;
    }
}