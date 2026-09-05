package com.nexis.integrity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.nexis.baseline.BaselineEntry;
import com.nexis.baseline.BaselineManager;
import com.nexis.scanner.FileScanner;

/**
 * Core integrity comparison engine responsible for comparing current filesystem
 * state against stored baseline data to detect file modifications, creations, and deletions.
 */
public class ComparisonEngine {

    private final FileScanner fileScanner;

    /**
     * Constructs a ComparisonEngine using a default FileScanner.
     */
    public ComparisonEngine() {
        this(new FileScanner());
    }

    /**
     * Constructs a ComparisonEngine with a specified FileScanner.
     *
     * @param fileScanner file scanner component to use
     * @throws NullPointerException if fileScanner is null
     */
    public ComparisonEngine(FileScanner fileScanner) {
        this.fileScanner = Objects.requireNonNull(fileScanner, "FileScanner cannot be null");
    }

    /**
     * Compares the files in the specified target directory against entries managed by the given BaselineManager.
     *
     * @param targetDirectory root directory to scan and compare
     * @param baselineManager manager containing the baseline entries
     * @return structured ComparisonResult containing status of all files
     * @throws IllegalArgumentException if targetDirectory or baselineManager is null, or targetDirectory is invalid
     * @throws IOException if an unrecoverable I/O error occurs during directory scanning
     */
    public ComparisonResult compare(Path targetDirectory, BaselineManager baselineManager) throws IOException {
        if (baselineManager == null) {
            throw new IllegalArgumentException("Baseline manager cannot be null");
        }
        return compare(targetDirectory, baselineManager.getAllEntries().values());
    }

    /**
     * Compares the files in the specified target directory against a collection of BaselineEntry objects.
     *
     * @param targetDirectory root directory to scan and compare
     * @param baselineEntries collection of baseline entries to compare against
     * @return structured ComparisonResult containing status of all files
     * @throws IllegalArgumentException if targetDirectory or baselineEntries is null, or targetDirectory is invalid
     * @throws IOException if an unrecoverable I/O error occurs during directory scanning
     */
    public ComparisonResult compare(Path targetDirectory, Collection<BaselineEntry> baselineEntries) throws IOException {
        if (targetDirectory == null) {
            throw new IllegalArgumentException("Target directory cannot be null");
        }
        if (baselineEntries == null) {
            throw new IllegalArgumentException("Baseline entries cannot be null");
        }

        List<Path> scannedFiles = fileScanner.scan(targetDirectory);
        Path normalizedRoot = targetDirectory.toAbsolutePath().normalize();

        Map<Path, BaselineEntry> baselineByAbsPath = new LinkedHashMap<>();
        for (BaselineEntry entry : baselineEntries) {
            if (entry == null || entry.filePath() == null) {
                continue;
            }
            Path p = entry.filePath();
            Path absPath = p.isAbsolute()
                ? p.normalize()
                : normalizedRoot.resolve(p).normalize();
            baselineByAbsPath.put(absPath, entry);
        }

        Map<Path, Path> scannedByAbsPath = new LinkedHashMap<>();
        for (Path scanned : scannedFiles) {
            if (scanned != null) {
                scannedByAbsPath.put(scanned.toAbsolutePath().normalize(), scanned);
            }
        }

        List<ComparisonEntry> resultEntries = new ArrayList<>();
        Map<Path, String> errors = new LinkedHashMap<>();

        // Process all scanned files on disk
        for (Map.Entry<Path, Path> entry : scannedByAbsPath.entrySet()) {
            Path absPath = entry.getKey();
            Path scannedFile = entry.getValue();
            BaselineEntry baselineEntry = baselineByAbsPath.get(absPath);

            String currentHash;
            try {
                currentHash = HashCalculator.calculateSha256(scannedFile);
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                errors.put(scannedFile, e.getMessage());
                continue;
            }

            if (baselineEntry == null) {
                // File exists on disk, but has no corresponding baseline entry
                resultEntries.add(new ComparisonEntry(scannedFile, ComparisonStatus.NEW, null, currentHash));
            } else {
                // File exists on disk and has a baseline entry
                String baselineHash = baselineEntry.sha256();
                if (currentHash.equalsIgnoreCase(baselineHash)) {
                    resultEntries.add(new ComparisonEntry(scannedFile, ComparisonStatus.UNCHANGED, baselineHash, currentHash));
                } else {
                    resultEntries.add(new ComparisonEntry(scannedFile, ComparisonStatus.MODIFIED, baselineHash, currentHash));
                }
            }
        }

        // Process baseline entries that were not discovered during scanning
        for (Map.Entry<Path, BaselineEntry> entry : baselineByAbsPath.entrySet()) {
            Path absPath = entry.getKey();
            BaselineEntry baselineEntry = entry.getValue();

            if (!scannedByAbsPath.containsKey(absPath)) {
                if (Files.exists(absPath) && Files.isRegularFile(absPath)) {
                    String currentHash;
                    try {
                        currentHash = HashCalculator.calculateSha256(absPath);
                    } catch (IOException | IllegalArgumentException | SecurityException e) {
                        errors.put(absPath, e.getMessage());
                        continue;
                    }
                    String baselineHash = baselineEntry.sha256();
                    Path pathRecord = baselineEntry.filePath().isAbsolute()
                        ? baselineEntry.filePath()
                        : targetDirectory.resolve(baselineEntry.filePath());
                    if (currentHash.equalsIgnoreCase(baselineHash)) {
                        resultEntries.add(new ComparisonEntry(pathRecord, ComparisonStatus.UNCHANGED, baselineHash, currentHash));
                    } else {
                        resultEntries.add(new ComparisonEntry(pathRecord, ComparisonStatus.MODIFIED, baselineHash, currentHash));
                    }
                } else {
                    // File no longer exists on disk
                    Path pathRecord = baselineEntry.filePath().isAbsolute()
                        ? baselineEntry.filePath()
                        : targetDirectory.resolve(baselineEntry.filePath());
                    resultEntries.add(new ComparisonEntry(pathRecord, ComparisonStatus.DELETED, baselineEntry.sha256(), null));
                }
            }
        }

        return new ComparisonResult(targetDirectory, resultEntries, errors);
    }
}