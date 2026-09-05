package com.nexis.integrity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nexis.baseline.BaselineEntry;
import com.nexis.baseline.BaselineManager;
import com.nexis.scanner.FileScanner;

class ComparisonEngineTest {

    @TempDir
    Path tempDir;

    private ComparisonEngine engine;
    private BaselineManager baselineManager;

    @BeforeEach
    void setUp() {
        engine = new ComparisonEngine();
        baselineManager = new BaselineManager(tempDir.resolve("baseline.json"));
    }

    @Test
    @DisplayName("1. Unchanged file is accurately classified as UNCHANGED")
    void unchangedFileIsDetectedAsUnchanged() throws IOException {
        Path file = tempDir.resolve("unchanged.txt");
        Files.writeString(file, "Strictly identical content", StandardCharsets.UTF_8);

        BaselineEntry baselineEntry = baselineManager.addOrUpdateFile(file);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(1, result.getTotalCount(), "Must contain exactly 1 entry");
        assertEquals(1, result.getUnchangedCount(), "Unchanged count must be 1");
        assertEquals(0, result.getModifiedCount());
        assertEquals(0, result.getNewCount());
        assertEquals(0, result.getDeletedCount());
        assertEquals(0, result.getErrorCount());
        assertTrue(result.isClean(), "Result must be clean");
        assertFalse(result.hasDifferences(), "Result must have no differences");

        ComparisonEntry entry = result.getUnchanged().get(0);
        assertEquals(ComparisonStatus.UNCHANGED, entry.status());
        assertEquals(file.normalize(), entry.filePath());
        assertEquals(baselineEntry.sha256(), entry.baselineHash());
        assertEquals(baselineEntry.sha256(), entry.currentHash());
        assertTrue(entry.isUnchanged());
        assertFalse(entry.isModified());
    }

    @Test
    @DisplayName("2. Modified file is accurately classified as MODIFIED with updated hash")
    void modifiedFileIsDetectedAsModified() throws IOException {
        Path file = tempDir.resolve("modified.txt");
        Files.writeString(file, "Original baseline content", StandardCharsets.UTF_8);

        BaselineEntry originalEntry = baselineManager.addOrUpdateFile(file);

        // Modify the file on disk
        Files.writeString(file, "Altered tamper content", StandardCharsets.UTF_8);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getUnchangedCount());
        assertEquals(1, result.getModifiedCount());
        assertEquals(0, result.getNewCount());
        assertEquals(0, result.getDeletedCount());
        assertTrue(result.hasDifferences(), "Result must report differences");
        assertFalse(result.isClean(), "Result must not be clean");

        ComparisonEntry entry = result.getModified().get(0);
        assertEquals(ComparisonStatus.MODIFIED, entry.status());
        assertEquals(file.normalize(), entry.filePath());
        assertEquals(originalEntry.sha256(), entry.baselineHash());
        assertNotNull(entry.currentHash());
        assertFalse(entry.baselineHash().equalsIgnoreCase(entry.currentHash()));
        assertTrue(entry.isModified());
    }

    @Test
    @DisplayName("3. Newly created file is accurately classified as NEW")
    void newlyCreatedFileIsDetectedAsNew() throws IOException {
        Path newFile = tempDir.resolve("brand_new.txt");
        Files.writeString(newFile, "Freshly created file content", StandardCharsets.UTF_8);

        // Baseline has nothing added
        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getUnchangedCount());
        assertEquals(0, result.getModifiedCount());
        assertEquals(1, result.getNewCount());
        assertEquals(0, result.getDeletedCount());
        assertTrue(result.hasDifferences());

        ComparisonEntry entry = result.getNewFiles().get(0);
        assertEquals(ComparisonStatus.NEW, entry.status());
        assertEquals(newFile.normalize(), entry.filePath());
        assertTrue(entry.getBaselineHash().isEmpty(), "Baseline hash must be absent for new file");
        assertTrue(entry.getCurrentHash().isPresent(), "Current hash must be present for new file");
        assertEquals(HashCalculator.calculateSha256(newFile), entry.currentHash());
        assertTrue(entry.isNew());
    }

    @Test
    @DisplayName("4. Deleted file is accurately classified as DELETED with preserved baseline hash")
    void deletedFileIsDetectedAsDeleted() throws IOException {
        Path file = tempDir.resolve("to_be_deleted.txt");
        Files.writeString(file, "Temporary ephemeral payload", StandardCharsets.UTF_8);

        BaselineEntry entry = baselineManager.addOrUpdateFile(file);

        // Delete file from disk
        Files.delete(file);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getUnchangedCount());
        assertEquals(0, result.getModifiedCount());
        assertEquals(0, result.getNewCount());
        assertEquals(1, result.getDeletedCount());
        assertTrue(result.hasDifferences());

        ComparisonEntry deletedEntry = result.getDeleted().get(0);
        assertEquals(ComparisonStatus.DELETED, deletedEntry.status());
        assertEquals(file.normalize(), deletedEntry.filePath());
        assertEquals(entry.sha256(), deletedEntry.baselineHash());
        assertTrue(deletedEntry.getCurrentHash().isEmpty(), "Current hash must be empty for deleted file");
        assertTrue(deletedEntry.isDeleted());
    }

    @Test
    @DisplayName("5. Multiple files with mixed states are classified correctly in a single run")
    void multipleFilesWithMixedStates() throws IOException {
        Path unchangedFile = tempDir.resolve("unchanged.txt");
        Path modifiedFile = tempDir.resolve("modified.txt");
        Path deletedFile = tempDir.resolve("deleted.txt");
        Path newFile = tempDir.resolve("new.txt");

        Files.writeString(unchangedFile, "Initial unchanged payload", StandardCharsets.UTF_8);
        Files.writeString(modifiedFile, "Initial modified payload", StandardCharsets.UTF_8);
        Files.writeString(deletedFile, "Initial deleted payload", StandardCharsets.UTF_8);

        // Populate baseline with 3 files
        BaselineEntry unchangedBaseline = baselineManager.addOrUpdateFile(unchangedFile);
        BaselineEntry modifiedBaseline = baselineManager.addOrUpdateFile(modifiedFile);
        BaselineEntry deletedBaseline = baselineManager.addOrUpdateFile(deletedFile);

        // Alter filesystem: modify 1, delete 1, add 1
        Files.writeString(modifiedFile, "MODIFIED PAYLOAD CONTENT", StandardCharsets.UTF_8);
        Files.delete(deletedFile);
        Files.writeString(newFile, "BRAND NEW PAYLOAD", StandardCharsets.UTF_8);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(4, result.getTotalCount());
        assertEquals(1, result.getUnchangedCount());
        assertEquals(1, result.getModifiedCount());
        assertEquals(1, result.getNewCount());
        assertEquals(1, result.getDeletedCount());
        assertEquals(0, result.getErrorCount());
        assertTrue(result.hasDifferences());

        // Verify UNCHANGED
        Optional<ComparisonEntry> unchOpt = result.getEntry(unchangedFile);
        assertTrue(unchOpt.isPresent());
        assertEquals(ComparisonStatus.UNCHANGED, unchOpt.get().status());
        assertEquals(unchangedBaseline.sha256(), unchOpt.get().baselineHash());
        assertEquals(unchangedBaseline.sha256(), unchOpt.get().currentHash());

        // Verify MODIFIED
        Optional<ComparisonEntry> modOpt = result.getEntry(modifiedFile);
        assertTrue(modOpt.isPresent());
        assertEquals(ComparisonStatus.MODIFIED, modOpt.get().status());
        assertEquals(modifiedBaseline.sha256(), modOpt.get().baselineHash());
        assertEquals(HashCalculator.calculateSha256(modifiedFile), modOpt.get().currentHash());

        // Verify DELETED
        Optional<ComparisonEntry> delOpt = result.getEntry(deletedFile);
        assertTrue(delOpt.isPresent());
        assertEquals(ComparisonStatus.DELETED, delOpt.get().status());
        assertEquals(deletedBaseline.sha256(), delOpt.get().baselineHash());
        assertTrue(delOpt.get().getCurrentHash().isEmpty());

        // Verify NEW
        Optional<ComparisonEntry> newOpt = result.getEntry(newFile);
        assertTrue(newOpt.isPresent());
        assertEquals(ComparisonStatus.NEW, newOpt.get().status());
        assertTrue(newOpt.get().getBaselineHash().isEmpty());
        assertEquals(HashCalculator.calculateSha256(newFile), newOpt.get().currentHash());
    }

    @Test
    @DisplayName("6. Empty baseline with files on disk classifies all files as NEW")
    void emptyBaselineClassifiesAllFilesAsNew() throws IOException {
        Files.writeString(tempDir.resolve("fileA.txt"), "A content", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("fileB.txt"), "B content", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("fileC.txt"), "C content", StandardCharsets.UTF_8);

        assertTrue(baselineManager.isEmpty());

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(3, result.getTotalCount());
        assertEquals(0, result.getUnchangedCount());
        assertEquals(0, result.getModifiedCount());
        assertEquals(3, result.getNewCount());
        assertEquals(0, result.getDeletedCount());
        assertEquals(3, result.getNewPaths().size());
    }

    @Test
    @DisplayName("7. Empty directory with baseline entries classifies all baseline entries as DELETED")
    void emptyDirectoryClassifiesAllBaselineEntriesAsDeleted() throws IOException {
        Path ghost1 = tempDir.resolve("ghost1.txt");
        Path ghost2 = tempDir.resolve("ghost2.txt");

        baselineManager.addOrUpdateEntry(new BaselineEntry(
            ghost1,
            "1111111111111111111111111111111111111111111111111111111111111111"
        ));
        baselineManager.addOrUpdateEntry(new BaselineEntry(
            ghost2,
            "2222222222222222222222222222222222222222222222222222222222222222"
        ));

        assertEquals(2, baselineManager.getEntryCount());

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(2, result.getTotalCount());
        assertEquals(0, result.getUnchangedCount());
        assertEquals(0, result.getModifiedCount());
        assertEquals(0, result.getNewCount());
        assertEquals(2, result.getDeletedCount());
        assertEquals(2, result.getDeletedPaths().size());
    }

    @Test
    @DisplayName("8. Empty baseline and empty directory returns an empty result")
    void emptyBaselineAndEmptyDirectoryReturnsEmptyResult() throws IOException {
        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(0, result.getTotalCount());
        assertTrue(result.isEmpty());
        assertTrue(result.isClean());
        assertFalse(result.hasDifferences());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("9. Nested subdirectories are scanned and compared recursively")
    void nestedDirectoriesHandledRecursively() throws IOException {
        Path sub1 = Files.createDirectory(tempDir.resolve("sub1"));
        Path sub2 = Files.createDirectories(sub1.resolve("deep"));

        Path file1 = tempDir.resolve("root.txt");
        Path file2 = sub1.resolve("middle.txt");
        Path file3 = sub2.resolve("leaf.txt");

        Files.writeString(file1, "Root text", StandardCharsets.UTF_8);
        Files.writeString(file2, "Middle text", StandardCharsets.UTF_8);
        Files.writeString(file3, "Leaf text", StandardCharsets.UTF_8);

        baselineManager.addOrUpdateFile(file1);
        baselineManager.addOrUpdateFile(file2);
        baselineManager.addOrUpdateFile(file3);

        // Modify leaf, leave others unchanged
        Files.writeString(file3, "MODIFIED LEAF TEXT", StandardCharsets.UTF_8);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getUnchangedCount());
        assertEquals(1, result.getModifiedCount());

        assertTrue(result.getEntry(file1).orElseThrow().isUnchanged());
        assertTrue(result.getEntry(file2).orElseThrow().isUnchanged());
        assertTrue(result.getEntry(file3).orElseThrow().isModified());
    }

    @Test
    @DisplayName("10. Relative baseline entries match scanned files correctly")
    void relativeBaselineEntriesMatchScannedFiles() throws IOException {
        Path file = tempDir.resolve("relative_test.txt");
        Files.writeString(file, "Relative test content", StandardCharsets.UTF_8);

        String hash = HashCalculator.calculateSha256(file);
        // Create baseline entry using relative path
        BaselineEntry relativeEntry = new BaselineEntry(Path.of("relative_test.txt"), hash);

        ComparisonResult result = engine.compare(tempDir, List.of(relativeEntry));

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getUnchangedCount());
        assertEquals(0, result.getModifiedCount());
        assertEquals(0, result.getNewCount());
        assertEquals(0, result.getDeletedCount());

        // Can look up via relative or absolute path
        assertTrue(result.getEntry(Path.of("relative_test.txt")).isPresent());
        assertTrue(result.getEntry(file).isPresent());
    }

    @Test
    @DisplayName("11. Entry lookup works with exact, absolute, and relative paths")
    void entryLookupWorksWithVariousPathRepresentations() throws IOException {
        Path sub = Files.createDirectory(tempDir.resolve("folder"));
        Path file = sub.resolve("item.txt");
        Files.writeString(file, "Item content", StandardCharsets.UTF_8);

        baselineManager.addOrUpdateFile(file);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertTrue(result.getEntry(file).isPresent(), "Exact path should find entry");
        assertTrue(result.getEntry(file.toAbsolutePath()).isPresent(), "Absolute path should find entry");
        assertTrue(result.getEntry(Path.of("folder", "item.txt")).isPresent(), "Relative path should find entry");
        assertTrue(result.getEntry(null).isEmpty(), "Null path lookup should return empty");
        assertTrue(result.getEntry(Path.of("nonexistent.txt")).isEmpty(), "Nonexistent path should return empty");
    }

    @Test
    @DisplayName("12. Null target directory throws IllegalArgumentException")
    void nullTargetDirectoryThrowsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> engine.compare(null, baselineManager),
            "Target directory cannot be null"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> engine.compare(null, Collections.emptyList()),
            "Target directory cannot be null"
        );
    }

    @Test
    @DisplayName("13. Null baseline argument throws IllegalArgumentException")
    void nullBaselineThrowsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> engine.compare(tempDir, (BaselineManager) null),
            "BaselineManager cannot be null"
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> engine.compare(tempDir, (List<BaselineEntry>) null),
            "Baseline collection cannot be null"
        );
    }

    @Test
    @DisplayName("14. Nonexistent target directory throws IllegalArgumentException")
    void nonexistentTargetDirectoryThrowsException() {
        Path nonexistent = tempDir.resolve("nonexistent_dir");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> engine.compare(nonexistent, baselineManager)
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("15. Regular file as target directory throws IllegalArgumentException")
    void regularFileAsTargetDirectoryThrowsException() throws IOException {
        Path regularFile = tempDir.resolve("file.txt");
        Files.createFile(regularFile);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> engine.compare(regularFile, baselineManager)
        );
        assertTrue(ex.getMessage().contains("not a directory"));
    }

    @Test
    @DisplayName("16. Hashing failure on problematic file is recorded in errors without aborting comparison")
    void hashingFailureRecordedInErrorsWithoutCrashing() throws IOException {
        Path healthyFile = tempDir.resolve("healthy.txt");
        Files.writeString(healthyFile, "Healthy file content", StandardCharsets.UTF_8);

        // A mock file scanner that returns healthyFile and a problematic path that cannot be hashed
        Path phantomFile = tempDir.resolve("phantom.txt"); // Not created on disk

        FileScanner scannerWithPhantom = new FileScanner() {
            @Override
            public List<Path> scan(Path root) throws IOException {
                return List.of(healthyFile, phantomFile);
            }
        };

        ComparisonEngine customEngine = new ComparisonEngine(scannerWithPhantom);
        ComparisonResult result = customEngine.compare(tempDir, Collections.emptyList());

        // Healthy file should be detected as NEW
        assertEquals(1, result.getNewCount());
        assertEquals(healthyFile.normalize(), result.getNewFiles().get(0).filePath());

        // Problematic phantom file should be in errors
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrorCount());
        assertTrue(result.getErrors().containsKey(phantomFile));
        assertTrue(result.hasDifferences());
    }

    @Test
    @DisplayName("17. ComparisonEntry constructor validations and accessors")
    void comparisonEntryValidations() {
        Path p = Path.of("test.txt");

        assertThrows(NullPointerException.class, () -> new ComparisonEntry(null, ComparisonStatus.NEW, null, "abc"));
        assertThrows(NullPointerException.class, () -> new ComparisonEntry(p, null, null, "abc"));

        // Hashes converted to lowercase
        ComparisonEntry entry = new ComparisonEntry(
            p,
            ComparisonStatus.MODIFIED,
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        );

        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", entry.baselineHash());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", entry.currentHash());
        assertEquals(p.normalize(), entry.getFilePath());
        assertEquals(ComparisonStatus.MODIFIED, entry.getStatus());
        assertTrue(entry.getBaselineHash().isPresent());
        assertTrue(entry.getCurrentHash().isPresent());
    }

    @Test
    @DisplayName("18. ComparisonResult path list accessors and toString")
    void comparisonResultPathListAccessorsAndToString() throws IOException {
        Path f1 = tempDir.resolve("a.txt");
        Path f2 = tempDir.resolve("b.txt");
        Files.writeString(f1, "1", StandardCharsets.UTF_8);
        Files.writeString(f2, "2", StandardCharsets.UTF_8);

        baselineManager.addOrUpdateFile(f1);

        ComparisonResult result = engine.compare(tempDir, baselineManager);

        assertEquals(List.of(f1.normalize()), result.getUnchangedPaths());
        assertEquals(List.of(f2.normalize()), result.getNewPaths());
        assertTrue(result.getModifiedPaths().isEmpty());
        assertTrue(result.getDeletedPaths().isEmpty());

        String str = result.toString();
        assertTrue(str.contains("total=2"));
        assertTrue(str.contains("unchanged=1"));
        assertTrue(str.contains("new=1"));
    }
}