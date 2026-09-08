package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.nexis.alert.AlertManager;
import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.SecurityLogger;
import com.nexis.alert.Severity;
import com.nexis.report.EventRepository;
import com.nexis.baseline.BaselineManager;
import com.nexis.baseline.BaselineStorageException;
import com.nexis.integrity.ComparisonEngine;
import com.nexis.integrity.ComparisonEntry;
import com.nexis.integrity.ComparisonResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * CLI subcommand that performs an integrity scan against the stored baseline.
 *
 * <p>After the existing {@link ResultFormatter} summary is printed, each
 * comparison result entry is mapped to a {@link SecurityEvent} and dispatched
 * through both {@link AlertManager} (CLI) and {@link SecurityLogger} (log file):
 * <ul>
 *   <li>MODIFIED files → {@code INTEGRITY_VIOLATION / CRITICAL}</li>
 *   <li>DELETED files  → {@code FILE_DELETED / WARNING}</li>
 *   <li>NEW files      → {@code FILE_CREATED / INFO}</li>
 *   <li>Scan errors    → {@code SYSTEM_ERROR / ERROR}</li>
 * </ul>
 */
@Command(
    name = "scan",
    description = "Scan a directory and compare against the integrity baseline",
    mixinStandardHelpOptions = true
)
public class ScanCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory to scan")
    private Path directory;

    @ParentCommand
    private NexisCLI parent;

    @Override
    public Integer call() {
        PrintWriter out = parent != null && parent.getOut() != null
            ? parent.getOut()
            : new PrintWriter(System.out, true);
        PrintWriter err = parent != null && parent.getErr() != null
            ? parent.getErr()
            : new PrintWriter(System.err, true);

        directory = directory.toAbsolutePath().normalize();

        if (!Files.exists(directory)) {
            err.println("Error: Directory does not exist: " + directory);
            return 1;
        }
        if (!Files.isDirectory(directory)) {
            err.println("Error: Path is not a directory: " + directory);
            return 1;
        }
        if (!Files.isReadable(directory)) {
            err.println("Error: Directory is not accessible: " + directory);
            return 1;
        }

        BaselineManager manager = new BaselineManager();
        try {
            manager.load();
        } catch (BaselineStorageException e) {
            err.println("Error: No baseline found. Run 'nexis baseline <directory>' first.");
            err.println("  Detail: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("Error: Failed to load baseline — " + e.getMessage());
            return 1;
        }

        try {
            ComparisonEngine engine = new ComparisonEngine();
            ComparisonResult result = engine.compare(directory, manager);

            // Print the existing formatted summary (unchanged from previous days)
            ResultFormatter.format(result, directory, out);

            // Emit security events for all notable findings
            AlertManager alertManager = new AlertManager(out);
            SecurityLogger securityLogger = new SecurityLogger();
            EventRepository eventRepository;
            try {
                eventRepository = EventRepository.loadOrDefault();
            } catch (IOException e) {
                err.println("Error: Failed to load event repository — " + e.getMessage());
                return 1;
            }

            emitIntegrityEvents(result, alertManager, securityLogger, eventRepository);
            try {
                eventRepository.save();
            } catch (IOException e) {
                // Non-fatal, reporting storage failure must not crash scan
                err.println("[NEXIS] Warning: Failed to save event repository — " + e.getMessage());
            }

            return result.isClean() ? 0 : 1;

        } catch (IOException e) {
            err.println("Error: Scan failed — " + e.getMessage());
            return 1;
        }
    }

    /**
     * Iterates the {@link ComparisonResult} and emits one {@link SecurityEvent}
     * per notable entry to alert manager, security logger, and event repository.
     *
     * <p>Only actionable findings are emitted — UNCHANGED files produce no event.
     *
     * @param result          the comparison result from the integrity engine
     * @param alertManager    alert display component
     * @param securityLogger  persistent logging component
     * @param eventRepository event repository for reporting
     */
    private static void emitIntegrityEvents(ComparisonResult result,
                                            AlertManager alertManager,
                                            SecurityLogger securityLogger,
                                            EventRepository eventRepository) {
        // MODIFIED → INTEGRITY_VIOLATION / CRITICAL (hash mismatch = tamper indicator)
        for (ComparisonEntry entry : result.getModified()) {
            String details = "SHA-256 hash differs from baseline — possible tampering detected."
                + entry.getBaselineHash().map(h -> " Expected: " + h).orElse("")
                + entry.getCurrentHash().map(h -> " Found: " + h).orElse("");
            SecurityEvent event = SecurityEvent.of(
                EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, entry.filePath(), details);
            alertManager.alert(event);
            securityLogger.log(event);
            eventRepository.add(event);
        }

        // DELETED → FILE_DELETED / WARNING
        for (ComparisonEntry entry : result.getDeleted()) {
            SecurityEvent event = SecurityEvent.of(
                EventType.FILE_DELETED, Severity.WARNING, entry.filePath(),
                "Baselined file no longer exists on disk.");
            alertManager.alert(event);
            securityLogger.log(event);
            eventRepository.add(event);
        }

        // NEW → FILE_CREATED / INFO
        for (ComparisonEntry entry : result.getNewFiles()) {
            SecurityEvent event = SecurityEvent.of(
                EventType.FILE_CREATED, Severity.INFO, entry.filePath(),
                "File exists on disk but has no baseline entry.");
            alertManager.alert(event);
            securityLogger.log(event);
            eventRepository.add(event);
        }

        // Scan errors → SYSTEM_ERROR / ERROR
        for (Map.Entry<Path, String> error : result.getErrors().entrySet()) {
            SecurityEvent event = SecurityEvent.of(
                EventType.SYSTEM_ERROR, Severity.ERROR, error.getKey(),
                "Scan error: " + error.getValue());
            alertManager.alert(event);
            securityLogger.log(event);
            eventRepository.add(event);
        }
    }
}
