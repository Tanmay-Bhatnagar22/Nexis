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
import com.nexis.integrity.HashCalculator;
import com.nexis.monitor.DirectoryMonitor;
import com.nexis.monitor.MonitorEvent;

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

        AlertManager alertManager = new AlertManager(out);
        SecurityLogger securityLogger = new SecurityLogger();

        // try-with-resources guarantees the WatchService is closed on every exit path
        try (DirectoryMonitor monitor = new DirectoryMonitor(directory)) {

            // Shutdown hook ensures the WatchService is closed on Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                monitor.stop();
                out.println();
                out.println("[WATCH] Stopped.");
                out.flush();
            }));

            out.println("[WATCH] Monitoring: " + directory);
            out.println("[WATCH] Alerts and events are logged to: " + SecurityLogger.DEFAULT_LOG_PATH.toAbsolutePath().normalize());
            out.flush();

            // Blocks until stop() is called (e.g. via Ctrl+C shutdown hook)
            monitor.start(event -> dispatchEvent(event, alertManager, securityLogger));

        } catch (IOException e) {
            err.println("Error: Failed to initialize file watcher — " + e.getMessage());
            return 1;
        }

        return 0;
    }

    /**
     * Translates a raw {@link MonitorEvent} into a {@link SecurityEvent} and
     * dispatches it to both the alert manager and security logger.
     *
     * <p>Event mappings:
     * <ul>
     *   <li>CREATED  → FILE_CREATED / INFO</li>
     *   <li>MODIFIED → FILE_MODIFIED / WARNING</li>
     *   <li>DELETED  → FILE_DELETED / WARNING</li>
     * </ul>
     *
     * @param event         the raw filesystem event from the WatchService
     * @param alertManager  alert display component
     * @param securityLogger persistent logging component
     */
    private void dispatchEvent(MonitorEvent event,
                               AlertManager alertManager,
                               SecurityLogger securityLogger) {
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
                    String hash = tryHash(file);
                    String details = hash != null
                        ? "File modification detected. SHA-256: " + hash
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

        } catch (Exception e) {
            // Unexpected error in event dispatch — report as MONITORING_ERROR, never crash the loop
            SecurityEvent errEvent = SecurityEvent.of(
                EventType.MONITORING_ERROR,
                Severity.ERROR,
                "Unexpected error handling event for " + file + ": " + e.getMessage()
            );
            alertManager.alert(errEvent);
            securityLogger.log(errEvent);
        }
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
