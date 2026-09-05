package com.nexis.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;

import com.nexis.integrity.ComparisonEntry;
import com.nexis.integrity.ComparisonResult;

/**
 * Formats a {@link ComparisonResult} for display on the CLI.
 */
public final class ResultFormatter {

    private static final String SEPARATOR = "────────────────────────────────────";

    private ResultFormatter() {
        // Utility class: prevent instantiation
    }

    /**
     * Renders a complete integrity scan report to the given writer.
     *
     * @param result the comparison result to format
     * @param target the scanned target directory
     * @param out    the writer to output to
     */
    public static void format(ComparisonResult result, Path target, PrintWriter out) {
        out.println();
        out.println("  NEXIS FILE INTEGRITY SCAN");
        out.println("  Target: " + target.toAbsolutePath().normalize());
        out.println();

        out.println("  Integrity Results");
        out.println("  " + SEPARATOR);
        out.printf("  %-14s %d%n", "UNCHANGED", result.getUnchangedCount());
        out.printf("  %-14s %d%n", "MODIFIED", result.getModifiedCount());
        out.printf("  %-14s %d%n", "NEW", result.getNewCount());
        out.printf("  %-14s %d%n", "DELETED", result.getDeletedCount());
        out.println("  " + SEPARATOR);
        out.printf("  %-14s %d%n", "Total", result.getTotalCount());

        if (result.getErrorCount() > 0) {
            out.printf("  %-14s %d%n", "Errors", result.getErrorCount());
        }
        out.println();

        // Details for modified files
        for (ComparisonEntry entry : result.getModified()) {
            out.println("  Modified: \u26a0 " + fileName(entry.filePath(), target));
        }

        // Details for new files
        for (ComparisonEntry entry : result.getNewFiles()) {
            out.println("  New:      + " + fileName(entry.filePath(), target));
        }

        // Details for deleted files
        for (ComparisonEntry entry : result.getDeleted()) {
            out.println("  Deleted:  \u2717 " + fileName(entry.filePath(), target));
        }

        // Details for errors
        if (result.hasErrors()) {
            out.println();
            for (Map.Entry<Path, String> error : result.getErrors().entrySet()) {
                out.println("  Error:    ! " + fileName(error.getKey(), target) + " — " + error.getValue());
            }
        }

        out.println();
        if (result.isClean()) {
            out.println("  Status: CLEAN — All files match baseline.");
        } else {
            out.println("  Status: DIFFERENCES DETECTED — Integrity violations found.");
        }
        out.println();
        out.flush();
    }

    /**
     * Returns a display-friendly file name, relativized against the target directory when possible.
     */
    private static String fileName(Path file, Path target) {
        try {
            Path absFile = file.toAbsolutePath().normalize();
            Path absTarget = target.toAbsolutePath().normalize();
            if (absFile.startsWith(absTarget)) {
                return absTarget.relativize(absFile).toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to absolute path
        }
        return file.toString();
    }
}
