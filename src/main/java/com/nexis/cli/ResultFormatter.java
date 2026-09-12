package com.nexis.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import com.nexis.integrity.ComparisonEntry;
import com.nexis.integrity.ComparisonResult;

/**
 * Formats a {@link ComparisonResult} for display on the CLI.
 *
 * <p>Produces clean, structured, technical output following cybersecurity and
 * DFIR tool conventions. Uses strict 7-bit US-ASCII rendering without Unicode
 * box-drawing or mojibake-prone symbols.
 */
public final class ResultFormatter {

    private ResultFormatter() {
        // Utility class: prevent instantiation
    }

    /**
     * Renders a complete integrity scan report to the given writer without an explicit baseline path.
     *
     * @param result the comparison result to format
     * @param target the scanned target directory
     * @param out    the writer to output to
     */
    public static void format(ComparisonResult result, Path target, PrintWriter out) {
        format(result, target, null, out);
    }

    /**
     * Renders a complete integrity scan report to the given writer with an explicit baseline path.
     *
     * @param result       the comparison result to format
     * @param target       the scanned target directory
     * @param baselinePath the baseline file path used for comparison, or null if unspecified
     * @param out          the writer to output to
     */
    public static void format(ComparisonResult result, Path target, Path baselinePath, PrintWriter out) {
        Objects.requireNonNull(result, "ComparisonResult cannot be null");
        Objects.requireNonNull(target, "Target path cannot be null");
        Objects.requireNonNull(out, "PrintWriter cannot be null");

        Path normTarget = target.toAbsolutePath().normalize();

        out.println();
        out.println(CliUI.SEPARATOR_DOUBLE);
        out.println("NEXIS SCAN");
        out.println(CliUI.SEPARATOR_DOUBLE);
        out.println();
        out.println("Target:");
        out.println("  " + normTarget);
        if (baselinePath != null) {
            out.println();
            out.println("Baseline:");
            out.println("  " + baselinePath.toAbsolutePath().normalize());
        }
        out.println();
        out.println(CliUI.SEPARATOR_SINGLE);
        out.println();

        if (result.isClean()) {
            out.println("SCAN RESULTS");
            out.println();
            out.println("  " + CliUI.success("No integrity violations detected. Status: CLEAN"));
            out.println();
            out.printf("  Files scanned:       %d%n", result.getTotalCount());
            out.printf("  Files unchanged:     %d  (UNCHANGED)%n", result.getUnchangedCount());
            out.printf("  Files modified:      %d  (MODIFIED)%n", result.getModifiedCount());
            out.printf("  Files created:       %d  (NEW)%n", result.getNewCount());
            out.printf("  Files deleted:       %d  (DELETED)%n", result.getDeletedCount());
            if (result.getErrorCount() > 0) {
                out.printf("  Errors:              %d%n", result.getErrorCount());
            }
            out.println();
            out.println(CliUI.SEPARATOR_SINGLE);
            out.println("Scan completed successfully.");
            out.println(CliUI.SEPARATOR_DOUBLE);
        } else {
            // Report specific modified / integrity violation entries
            for (ComparisonEntry entry : result.getModified()) {
                out.println(CliUI.critical("INTEGRITY VIOLATION DETECTED (MODIFIED)"));
                out.println();
                out.println("  File:");
                out.println("    " + fileName(entry.filePath(), target));
                out.println();
                out.println("  Expected SHA-256:");
                out.println("    " + entry.getBaselineHash().orElse("N/A"));
                out.println();
                out.println("  Actual SHA-256:");
                out.println("    " + entry.getCurrentHash().orElse("N/A"));
                out.println();
            }

            // Report new files
            for (ComparisonEntry entry : result.getNewFiles()) {
                out.println(CliUI.info("NEW FILE DETECTED (NEW)"));
                out.println("  File: " + fileName(entry.filePath(), target));
                if (entry.getCurrentHash().isPresent()) {
                    out.println("  SHA-256: " + entry.getCurrentHash().get());
                }
                out.println();
            }

            // Report deleted files
            for (ComparisonEntry entry : result.getDeleted()) {
                out.println(CliUI.warning("FILE DELETED (DELETED)"));
                out.println("  File: " + fileName(entry.filePath(), target));
                if (entry.getBaselineHash().isPresent()) {
                    out.println("  Baseline SHA-256: " + entry.getBaselineHash().get());
                }
                out.println();
            }

            // Report errors
            if (result.hasErrors()) {
                for (Map.Entry<Path, String> error : result.getErrors().entrySet()) {
                    out.println(CliUI.error("SCAN ERROR"));
                    out.println("  File:    " + fileName(error.getKey(), target));
                    out.println("  Details: " + error.getValue());
                    out.println();
                }
            }

            out.println(CliUI.SEPARATOR_SINGLE);
            out.println();
            out.println("SUMMARY");
            out.println();
            out.println("  Status: DIFFERENCES DETECTED");
            out.println();
            out.printf("  Files scanned:        %d%n", result.getTotalCount());
            out.printf("  Files unchanged:      %d  (UNCHANGED)%n", result.getUnchangedCount());
            out.printf("  Files modified:       %d  (MODIFIED)%n", result.getModifiedCount());
            out.printf("  Files created:        %d  (NEW)%n", result.getNewCount());
            out.printf("  Files deleted:        %d  (DELETED)%n", result.getDeletedCount());
            out.printf("  Integrity violations: %d%n", result.getModifiedCount());
            if (result.getErrorCount() > 0) {
                out.printf("  Errors:               %d%n", result.getErrorCount());
            }
            out.println();
            out.println(CliUI.SEPARATOR_DOUBLE);
        }

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
