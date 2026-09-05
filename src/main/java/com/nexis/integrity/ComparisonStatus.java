package com.nexis.integrity;

/**
 * Represents the integrity state of a file when comparing current filesystem state
 * against a baseline.
 */
public enum ComparisonStatus {

    /**
     * File exists on disk and its current SHA-256 cryptographic hash matches the baseline hash.
     */
    UNCHANGED,

    /**
     * File exists on disk but its current SHA-256 cryptographic hash differs from the baseline hash.
     */
    MODIFIED,

    /**
     * File exists currently on disk but has no corresponding baseline entry.
     */
    NEW,

    /**
     * Baseline entry exists but the file no longer exists on disk.
     */
    DELETED
}