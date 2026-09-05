package com.nexis.integrity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured, immutable container holding the results of an integrity comparison.
 */
public class ComparisonResult {

    private final Path rootDirectory;
    private final List<ComparisonEntry> entries;
    private final Map<Path, String> errors;

    private final List<ComparisonEntry> unchanged;
    private final List<ComparisonEntry> modified;
    private final List<ComparisonEntry> newFiles;
    private final List<ComparisonEntry> deleted;

    private final Map<Path, ComparisonEntry> byExactPath;
    private final Map<Path, ComparisonEntry> byAbsolutePath;
    private final Map<Path, ComparisonEntry> byRelativePath;

    /**
     * Constructs a ComparisonResult with entries and an empty error map.
     *
     * @param entries collection of comparison entries
     */
    public ComparisonResult(Collection<ComparisonEntry> entries) {
        this(null, entries, Collections.emptyMap());
    }

    /**
     * Constructs a ComparisonResult with entries and errors.
     *
     * @param entries collection of comparison entries
     * @param errors  map of file paths to error descriptions encountered during comparison
     */
    public ComparisonResult(Collection<ComparisonEntry> entries, Map<Path, String> errors) {
        this(null, entries, errors);
    }

    /**
     * Constructs a ComparisonResult with root directory, entries, and errors.
     *
     * @param rootDirectory optional monitored root directory
     * @param entries       collection of comparison entries
     * @param errors        map of file paths to error descriptions encountered during comparison
     */
    public ComparisonResult(Path rootDirectory, Collection<ComparisonEntry> entries, Map<Path, String> errors) {
        Objects.requireNonNull(entries, "Entries collection cannot be null");
        Objects.requireNonNull(errors, "Errors map cannot be null");

        this.rootDirectory = rootDirectory != null ? rootDirectory.normalize() : null;

        List<ComparisonEntry> sortedEntries = entries.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ComparisonEntry::filePath))
            .toList();

        this.entries = sortedEntries;
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));

        List<ComparisonEntry> unch = new ArrayList<>();
        List<ComparisonEntry> mod = new ArrayList<>();
        List<ComparisonEntry> nw = new ArrayList<>();
        List<ComparisonEntry> del = new ArrayList<>();

        Map<Path, ComparisonEntry> exactMap = new LinkedHashMap<>();
        Map<Path, ComparisonEntry> absMap = new LinkedHashMap<>();
        Map<Path, ComparisonEntry> relMap = new LinkedHashMap<>();

        Path absRoot = this.rootDirectory != null ? this.rootDirectory.toAbsolutePath().normalize() : null;

        for (ComparisonEntry entry : sortedEntries) {
            switch (entry.status()) {
                case UNCHANGED -> unch.add(entry);
                case MODIFIED -> mod.add(entry);
                case NEW -> nw.add(entry);
                case DELETED -> del.add(entry);
            }
            Path p = entry.filePath();
            exactMap.put(p, entry);
            Path absP = p.toAbsolutePath().normalize();
            absMap.put(absP, entry);

            if (absRoot != null && absP.startsWith(absRoot)) {
                Path relP = absRoot.relativize(absP);
                relMap.put(relP, entry);
            }
        }

        this.unchanged = Collections.unmodifiableList(unch);
        this.modified = Collections.unmodifiableList(mod);
        this.newFiles = Collections.unmodifiableList(nw);
        this.deleted = Collections.unmodifiableList(del);

        this.byExactPath = Collections.unmodifiableMap(exactMap);
        this.byAbsolutePath = Collections.unmodifiableMap(absMap);
        this.byRelativePath = Collections.unmodifiableMap(relMap);
    }

    /**
     * Returns the root directory against which the comparison was performed, if specified.
     *
     * @return Optional containing the root directory Path, or empty Optional
     */
    public Optional<Path> getRootDirectory() {
        return Optional.ofNullable(rootDirectory);
    }

    /**
     * Returns an unmodifiable list of all comparison entries.
     *
     * @return unmodifiable list of ComparisonEntry
     */
    public List<ComparisonEntry> getEntries() {
        return entries;
    }

    /**
     * Returns an unmodifiable list of unchanged entries.
     *
     * @return unmodifiable list of UNCHANGED ComparisonEntry
     */
    public List<ComparisonEntry> getUnchanged() {
        return unchanged;
    }

    /**
     * Returns an unmodifiable list of modified entries.
     *
     * @return unmodifiable list of MODIFIED ComparisonEntry
     */
    public List<ComparisonEntry> getModified() {
        return modified;
    }

    /**
     * Returns an unmodifiable list of newly discovered file entries.
     *
     * @return unmodifiable list of NEW ComparisonEntry
     */
    public List<ComparisonEntry> getNewFiles() {
        return newFiles;
    }

    /**
     * Alias for {@link #getNewFiles()}.
     *
     * @return unmodifiable list of NEW ComparisonEntry
     */
    public List<ComparisonEntry> getNew() {
        return newFiles;
    }

    /**
     * Returns an unmodifiable list of deleted file entries.
     *
     * @return unmodifiable list of DELETED ComparisonEntry
     */
    public List<ComparisonEntry> getDeleted() {
        return deleted;
    }

    /**
     * Returns an unmodifiable map of errors encountered during comparison.
     *
     * @return unmodifiable map of Path to error message
     */
    public Map<Path, String> getErrors() {
        return errors;
    }

    /**
     * Returns paths of all unchanged files.
     *
     * @return list of Path
     */
    public List<Path> getUnchangedPaths() {
        return unchanged.stream().map(ComparisonEntry::filePath).toList();
    }

    /**
     * Returns paths of all modified files.
     *
     * @return list of Path
     */
    public List<Path> getModifiedPaths() {
        return modified.stream().map(ComparisonEntry::filePath).toList();
    }

    /**
     * Returns paths of all new files.
     *
     * @return list of Path
     */
    public List<Path> getNewPaths() {
        return newFiles.stream().map(ComparisonEntry::filePath).toList();
    }

    /**
     * Returns paths of all deleted files.
     *
     * @return list of Path
     */
    public List<Path> getDeletedPaths() {
        return deleted.stream().map(ComparisonEntry::filePath).toList();
    }

    /**
     * Looks up a comparison entry by file path (handling exact, absolute, and relative representations).
     *
     * @param path file path to look up
     * @return Optional containing the ComparisonEntry if found, or empty Optional otherwise
     */
    public Optional<ComparisonEntry> getEntry(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        Path normalized = path.normalize();
        ComparisonEntry found = byExactPath.get(normalized);
        if (found != null) {
            return Optional.of(found);
        }
        found = byAbsolutePath.get(normalized.toAbsolutePath().normalize());
        if (found != null) {
            return Optional.of(found);
        }
        return Optional.ofNullable(byRelativePath.get(normalized));
    }

    /**
     * Returns the total count of evaluated files.
     *
     * @return total entry count
     */
    public int getTotalCount() {
        return entries.size();
    }

    /**
     * Returns the count of unchanged files.
     *
     * @return count of UNCHANGED files
     */
    public int getUnchangedCount() {
        return unchanged.size();
    }

    /**
     * Returns the count of modified files.
     *
     * @return count of MODIFIED files
     */
    public int getModifiedCount() {
        return modified.size();
    }

    /**
     * Returns the count of new files.
     *
     * @return count of NEW files
     */
    public int getNewCount() {
        return newFiles.size();
    }

    /**
     * Returns the count of deleted files.
     *
     * @return count of DELETED files
     */
    public int getDeletedCount() {
        return deleted.size();
    }

    /**
     * Returns the count of errors encountered during comparison.
     *
     * @return error count
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Checks if any changes (modifications, new files, deletions) or errors were detected.
     *
     * @return true if there are differences or errors, false if completely unchanged
     */
    public boolean hasDifferences() {
        return !modified.isEmpty() || !newFiles.isEmpty() || !deleted.isEmpty() || !errors.isEmpty();
    }

    /**
     * Alias for {@link #hasDifferences()}.
     *
     * @return true if changes or errors are present
     */
    public boolean hasChanges() {
        return hasDifferences();
    }

    /**
     * Checks if any errors occurred during comparison.
     *
     * @return true if error count > 0
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Checks if the monitored state is completely clean (all baseline files unchanged, no new, deleted, or error files).
     *
     * @return true if clean, false otherwise
     */
    public boolean isClean() {
        return !hasDifferences();
    }

    /**
     * Checks if the comparison result has no entries and no errors.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return entries.isEmpty() && errors.isEmpty();
    }

    @Override
    public String toString() {
        return "ComparisonResult{" +
            "total=" + getTotalCount() +
            ", unchanged=" + getUnchangedCount() +
            ", modified=" + getModifiedCount() +
            ", new=" + getNewCount() +
            ", deleted=" + getDeletedCount() +
            ", errors=" + getErrorCount() +
            '}';
    }
}