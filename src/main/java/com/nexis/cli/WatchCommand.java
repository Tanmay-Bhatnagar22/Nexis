package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.nexis.alert.AlertManager;
import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.SecurityLogger;
import com.nexis.alert.Severity;
import com.nexis.baseline.BaselineEntry;
import com.nexis.baseline.BaselineManager;
import com.nexis.integrity.ComparisonEngine;
import com.nexis.integrity.ComparisonEntry;
import com.nexis.integrity.HashCalculator;
import com.nexis.monitor.DirectoryMonitor;
import com.nexis.monitor.MonitorEvent;
import com.nexis.report.EventRepository;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * CLI subcommand that starts real-time WatchService-based monitoring of a directory.
 *
 * <p>Usage: {@code nexis watch <directory>}
 *
 * <p>Reports ENTRY_CREATE, ENTRY_MODIFY, and ENTRY_DELETE events as they occur.
 * Each event is:
 * <ol>
 *   <li>Wrapped as a {@link SecurityEvent} with an appropriate {@link EventType} and {@link Severity}</li>
 *   <li>Displayed on the CLI via {@link AlertManager}</li>
 *   <li>Persisted to {@code logs/nexis.log} via {@link SecurityLogger}</li>
 * </ol>
 *
 * <p>Press Ctrl+C to stop monitoring.
 */
@Command(
    name = "watch",
    description = "Monitor a directory in real-time for file system changes",
    mixinStandardHelpOptions = true
)
public class WatchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory to watch")
    private Path directory;

    @ParentCommand
    private NexisCLI parent;

    private Path baselinePath;
    private Path logPath;
    private Path eventsPath;
    private BaselineManager baselineManager;
    private ComparisonEngine comparisonEngine;

    public WatchCommand() {
    }

    public WatchCommand(Path logPath, Path eventsPath) {
        this.logPath = logPath;
        this.eventsPath = eventsPath;
    }

    public WatchCommand(Path baselinePath, Path logPath, Path eventsPath) {
        this.baselinePath = baselinePath;
        this.logPath = logPath;
        this.eventsPath = eventsPath;
    }

    public WatchCommand(BaselineManager baselineManager, Path logPath, Path eventsPath) {
        this.baselineManager = baselineManager;
        this.logPath = logPath;
        this.eventsPath = eventsPath;
    }

    public WatchCommand(BaselineManager baselineManager, ComparisonEngine comparisonEngine,
                        Path logPath, Path eventsPath) {
        this.baselineManager = baselineManager;
        this.comparisonEngine = comparisonEngine;
        this.logPath = logPath;
        this.eventsPath = eventsPath;
    }

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
            err.println(CliUI.error("Error: Directory does not exist: " + directory));
            return 1;
        }
        if (!Files.isDirectory(directory)) {
            err.println(CliUI.error("Error: Path is not a directory: " + directory));
            return 1;
        }
        if (!Files.isReadable(directory)) {
            err.println(CliUI.error("Error: Directory is not accessible: " + directory));
            return 1;
        }

        Path effectiveLogPath = this.logPath != null
            ? this.logPath
            : (parent != null && parent.getLogPath() != null
                ? parent.getLogPath()
                : SecurityLogger.DEFAULT_LOG_PATH);

        Path effectiveEventsPath = this.eventsPath != null
            ? this.eventsPath
            : (parent != null && parent.getEventsPath() != null
                ? parent.getEventsPath()
                : EventRepository.DEFAULT_EVENTS_PATH);

        // Pre-load baseline if not already provided
        if (this.baselineManager == null) {
            Path effectiveBaselinePath = this.baselinePath != null
                ? this.baselinePath
                : (parent != null && parent.getBaselinePath() != null
                    ? parent.getBaselinePath()
                    : BaselineManager.DEFAULT_BASELINE_PATH);

            BaselineManager manager = new BaselineManager(effectiveBaselinePath);
            if (Files.exists(effectiveBaselinePath)) {
                try {
                    manager.load();
                } catch (IOException e) {
                    err.println(CliUI.warning("Warning: Failed to load baseline — " + e.getMessage()));
                }
            }
            this.baselineManager = manager;
        }

        if (this.comparisonEngine == null) {
            this.comparisonEngine = new ComparisonEngine();
        }

        AlertManager alertManager = new AlertManager(out);
        SecurityLogger securityLogger = new SecurityLogger(effectiveLogPath);
        EventRepository eventRepository;
        try {
            eventRepository = EventRepository.loadOrDefault(effectiveEventsPath);
        } catch (IOException e) {
            err.println(CliUI.error("Error: Failed to load event repository — " + e.getMessage()));
            return 1;
        }

        // try-with-resources guarantees the WatchService is closed on every exit path
        try (DirectoryMonitor monitor = new DirectoryMonitor(directory)) {

            // Shutdown hook ensures the WatchService is closed on Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                monitor.stop();
                try {
                    eventRepository.save();
                } catch (IOException ignored) {
                }
                out.println();
                out.println(CliUI.info("[WATCH] Monitoring stopped."));
                out.flush();
            }));

            out.println(CliUI.info("Monitoring started... Monitoring: " + directory));
            out.println("  Alerts and events are logged to: " + securityLogger.getLogPath().toAbsolutePath().normalize());
            out.flush();

            // Blocks until stop() is called (e.g. via Ctrl+C shutdown hook)
            monitor.start(event -> dispatchEvent(event, alertManager, securityLogger, eventRepository));

        } catch (IOException e) {
            err.println(CliUI.error("Error: Failed to initialize file watcher — " + e.getMessage()));
            return 1;
        }

        return 0;
    }

    /**
     * Translates a raw {@link MonitorEvent} into a {@link SecurityEvent} and
     * dispatches it to the alert manager, security logger, and event repository.
     *
     * <p>Event mappings:
     * <ul>
     *   <li>CREATED  → FILE_CREATED / INFO</li>
     *   <li>MODIFIED → INTEGRITY_VIOLATION / CRITICAL if hash differs from baseline,
     *                  FILE_MODIFIED / WARNING if hash is unchanged or unbaselined</li>
     *   <li>DELETED  → FILE_DELETED / WARNING</li>
     * </ul>
     *
     * @param event          the raw filesystem event from the WatchService
     * @param alertManager   alert display component
     * @param securityLogger persistent logging component
     * @param eventRepository event repository for reporting
     * @return the dispatched SecurityEvent
     */
    public SecurityEvent dispatchEvent(MonitorEvent event,
                                       AlertManager alertManager,
                                       SecurityLogger securityLogger,
                                       EventRepository eventRepository) {
        Path file = event.filePath();

        try {
            SecurityEvent secEvent = switch (event.eventType()) {
                case CREATED -> {
                    String hash = tryHash(file);
                    String details = hash != null
                        ? "File creation detected. SHA-256: " + hash
                        : "File creation detected.";
                    yield SecurityEvent.of(EventType.FILE_CREATED, Severity.INFO, file, details);
                }
                case MODIFIED -> {
                    String currentHash = tryHash(file);
                    BaselineManager bm = getBaselineManager();
                    ComparisonEngine ce = getComparisonEngine();

                    if (currentHash != null && bm != null) {
                        BaselineEntry baselineEntry = bm.getEntry(file).orElse(null);
                        if (baselineEntry != null) {
                            ComparisonEntry comparison = ce.compareFile(file, currentHash, baselineEntry);
                            if (comparison.isModified()) {
                                String details = "SHA-256 hash differs from baseline — possible tampering detected."
                                    + comparison.getBaselineHash().map(h -> " Expected: " + h).orElse("")
                                    + comparison.getCurrentHash().map(h -> " Found: " + h).orElse("");
                                yield SecurityEvent.of(EventType.INTEGRITY_VIOLATION, Severity.CRITICAL, file, details);
                            } else if (comparison.isUnchanged()) {
                                yield SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, file,
                                    "File modification detected (hash unchanged). SHA-256: " + currentHash);
                            }
                        }
                    }

                    String details = currentHash != null
                        ? "File modification detected. SHA-256: " + currentHash
                        : "File modification detected.";
                    yield SecurityEvent.of(EventType.FILE_MODIFIED, Severity.WARNING, file, details);
                }
                case DELETED -> SecurityEvent.of(
                    EventType.FILE_DELETED,
                    Severity.WARNING,
                    file,
                    "File deletion detected."
                );
            };

            alertManager.alert(secEvent);
            securityLogger.log(secEvent);
            eventRepository.add(secEvent);
            try {
                eventRepository.save();
            } catch (IOException ignored) {
            }

            return secEvent;

        } catch (Exception e) {
            // Unexpected error in event dispatch — report as MONITORING_ERROR, never crash the loop
            SecurityEvent errEvent = SecurityEvent.of(
                EventType.MONITORING_ERROR,
                Severity.ERROR,
                "Unexpected error handling event for " + file + ": " + e.getMessage()
            );
            alertManager.alert(errEvent);
            securityLogger.log(errEvent);
            eventRepository.add(errEvent);
            try {
                eventRepository.save();
            } catch (IOException ignored) {
            }
            return errEvent;
        }
    }

    private BaselineManager getBaselineManager() {
        if (this.baselineManager != null) {
            return this.baselineManager;
        }
        Path effectiveBaselinePath = this.baselinePath != null
            ? this.baselinePath
            : (parent != null && parent.getBaselinePath() != null
                ? parent.getBaselinePath()
                : BaselineManager.DEFAULT_BASELINE_PATH);

        BaselineManager manager = new BaselineManager(effectiveBaselinePath);
        if (Files.exists(effectiveBaselinePath)) {
            try {
                manager.load();
            } catch (IOException ignored) {
            }
        }
        this.baselineManager = manager;
        return this.baselineManager;
    }

    private ComparisonEngine getComparisonEngine() {
        if (this.comparisonEngine == null) {
            this.comparisonEngine = new ComparisonEngine();
        }
        return this.comparisonEngine;
    }

    /**
     * Attempts to compute the SHA-256 hash of a file. Returns {@code null} if the file
     * cannot be read (e.g. it was transient or a directory node).
     *
     * @param file path to hash
     * @return lowercase hex SHA-256 string, or null on any failure
     */
    private static String tryHash(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                return HashCalculator.calculateSha256(file);
            }
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            // File may have been removed immediately after the event — report without hash
        }
        return null;
    }
}
