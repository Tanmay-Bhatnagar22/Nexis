package com.nexis.baseline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.nexis.integrity.HashCalculator;

/**
 * Manages in-memory file baseline entries and orchestrates baseline creation,
 * updates, queries, and persistence.
 */
public class BaselineManager {

    public static final Path DEFAULT_BASELINE_PATH = Path.of("data", "baseline.json");

    private final Path defaultBaselinePath;
    private final BaselineStorage storage;
    private final Map<Path, BaselineEntry> entries = new LinkedHashMap<>();

    public BaselineManager() {
        this(DEFAULT_BASELINE_PATH, new BaselineStorage());
    }

    public BaselineManager(Path defaultBaselinePath) {
        this(defaultBaselinePath, new BaselineStorage());
    }

    public BaselineManager(Path defaultBaselinePath, BaselineStorage storage) {
        this.defaultBaselinePath = Objects.requireNonNull(defaultBaselinePath, "Baseline path cannot be null").normalize();
        this.storage = Objects.requireNonNull(storage, "BaselineStorage cannot be null");
    }

    /**
     * Calculates the SHA-256 hash of the specified file using {@link HashCalculator}
     * and adds or updates its baseline entry.
     *
     * @param file the path of the file to hash and store
     * @return the created or updated BaselineEntry
     * @throws IllegalArgumentException if file is null, does not exist, or is not a regular file
     * @throws IOException if an I/O error occurs while calculating the hash
     */
    public BaselineEntry addOrUpdateFile(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        String hash = HashCalculator.calculateSha256(file);
        BaselineEntry entry = new BaselineEntry(file, hash);
        entries.put(entry.filePath(), entry);
        return entry;
    }

    /**
     * Hashes and adds or updates a collection of files into the baseline.
     *
     * @param files collection of file paths to hash and add
     * @return list of created or updated BaselineEntry instances
     * @throws IllegalArgumentException if files is null or contains invalid file paths
     * @throws IOException if an I/O error occurs during hashing
     */
    public List<BaselineEntry> addOrUpdateFiles(Collection<Path> files) throws IOException {
        if (files == null) {
            throw new IllegalArgumentException("Files collection cannot be null");
        }

        List<BaselineEntry> addedEntries = new ArrayList<>();
        for (Path file : files) {
            addedEntries.add(addOrUpdateFile(file));
        }
        return Collections.unmodifiableList(addedEntries);
    }

    /**
     * Adds or updates a pre-calculated baseline entry.
     *
     * @param entry the baseline entry to add
     * @throws IllegalArgumentException if entry is null
     */
    public void addOrUpdateEntry(BaselineEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("BaselineEntry cannot be null");
        }
        entries.put(entry.filePath(), entry);
    }

    /**
     * Retrieves the baseline entry for the given file path, if present.
     *
     * @param file the file path to query
     * @return Optional containing the BaselineEntry if found, or empty Optional otherwise
     * @throws IllegalArgumentException if file is null
     */
    public Optional<BaselineEntry> getEntry(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("Target file path cannot be null");
        }
        return Optional.ofNullable(entries.get(file.normalize()));
    }

    /**
     * Checks whether an entry exists for the specified file path.
     *
     * @param file the file path to check
     * @return true if an entry exists, false otherwise
     * @throws IllegalArgumentException if file is null
     */
    public boolean hasEntry(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("Target file path cannot be null");
        }
        return entries.containsKey(file.normalize());
    }

    /**
     * Removes the baseline entry for the specified file path.
     *
     * @param file the file path whose entry should be removed
     * @return true if an entry existed and was removed, false otherwise
     * @throws IllegalArgumentException if file is null
     */
    public boolean removeEntry(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("Target file path cannot be null");
        }
        return entries.remove(file.normalize()) != null;
    }

    /**
     * Returns an unmodifiable map of all current baseline entries keyed by normalized Path.
     *
     * @return unmodifiable map of Path to BaselineEntry
     */
    public Map<Path, BaselineEntry> getAllEntries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * Returns an unmodifiable list of all current baseline entries.
     *
     * @return unmodifiable list of BaselineEntry
     */
    public List<BaselineEntry> getEntriesList() {
        return List.copyOf(entries.values());
    }

    /**
     * Returns the total number of baseline entries currently held in memory.
     *
     * @return number of entries
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Returns whether the baseline is currently empty.
     *
     * @return true if there are no entries, false otherwise
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Clears all in-memory baseline entries.
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Persists the baseline to the default baseline file path.
     *
     * @throws IOException if saving fails
     */
    public void save() throws IOException {
        save(this.defaultBaselinePath);
    }

    /**
     * Persists the baseline to the specified target path.
     *
     * @param targetPath destination file path
     * @throws IOException if saving fails
     */
    public void save(Path targetPath) throws IOException {
        storage.writeBaseline(targetPath, entries.values());
    }

    /**
     * Loads baseline entries from the default baseline file path.
     *
     * @throws IOException if loading fails or the baseline file does not exist
     */
    public void load() throws IOException {
        load(this.defaultBaselinePath);
    }

    /**
     * Loads baseline entries from the specified source path, replacing current entries.
     *
     * @param sourcePath file path to read from
     * @throws IOException if loading fails or the baseline file does not exist
     */
    public void load(Path sourcePath) throws IOException {
        List<BaselineEntry> loaded = storage.readBaseline(sourcePath);
        entries.clear();
        for (BaselineEntry entry : loaded) {
            entries.put(entry.filePath(), entry);
        }
    }

    /**
     * Returns the configured default baseline file path.
     *
     * @return default baseline Path
     */
    public Path getBaselinePath() {
        return defaultBaselinePath;
    }
}

