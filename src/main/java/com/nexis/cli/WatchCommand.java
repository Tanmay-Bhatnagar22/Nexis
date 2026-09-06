package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

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
 * Press Ctrl+C to stop monitoring.
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
            out.flush();

            // Blocks until stop() is called (e.g. via Ctrl+C shutdown hook)
            monitor.start(event -> handleEvent(event, out));

        } catch (IOException e) {
            err.println("Error: Failed to initialize file watcher — " + e.getMessage());
            return 1;
        }

        return 0;
    }

    /**
     * Handles a single monitor event: hashes the file when present and prints
     * a formatted one-line report.
     *
     * @param event the filesystem event to handle
     * @param out   the writer to print to
     */
    private void handleEvent(MonitorEvent event, PrintWriter out) {
        Path file = event.filePath();
        String name = directory.relativize(file).toString();

        switch (event.eventType()) {
            case CREATED -> {
                String hash = tryHash(file);
                if (hash != null) {
                    out.println("[CREATE] " + name + "  sha256: " + hash);
                } else {
                    out.println("[CREATE] " + name);
                }
            }
            case MODIFIED -> {
                String hash = tryHash(file);
                if (hash != null) {
                    out.println("[MODIFY] " + name + "  sha256: " + hash);
                } else {
                    out.println("[MODIFY] " + name);
                }
            }
            case DELETED -> out.println("[DELETE] " + name);
        }
        out.flush();
    }

    /**
     * Attempts to compute the SHA-256 hash of a file. Returns null if the file
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
