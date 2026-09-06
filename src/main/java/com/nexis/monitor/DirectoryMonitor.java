package com.nexis.monitor;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Watches a single directory for filesystem events (create, modify, delete) using
 * Java's built-in {@link WatchService}. Events are dispatched as {@link MonitorEvent}
 * objects to a caller-supplied handler.
 *
 * <p>Usage pattern:
 * <pre>{@code
 *   DirectoryMonitor monitor = new DirectoryMonitor(Path.of("/path/to/watch"));
 *   // Run start() on a background thread; it blocks until stop() is called.
 *   Thread t = new Thread(() -> monitor.start(event -> System.out.println(event)));
 *   t.start();
 *   // ... later ...
 *   monitor.stop();   // unblocks start() and closes the WatchService
 * }</pre>
 *
 * <p>This class implements {@link AutoCloseable}: wrapping it in a try-with-resources
 * block also calls {@link #stop()}.
 */
public class DirectoryMonitor implements AutoCloseable {

    private final Path directory;
    private final WatchService watchService;
    private volatile boolean running;

    /**
     * Creates a DirectoryMonitor for the specified directory.
     *
     * @param directory the directory to monitor; must exist and be a directory
     * @throws IllegalArgumentException if {@code directory} is null, does not exist,
     *                                  or is not a directory
     * @throws IOException              if the underlying WatchService cannot be created
     */
    public DirectoryMonitor(Path directory) throws IOException {
        Objects.requireNonNull(directory, "Directory cannot be null");

        Path normalized = directory.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("Directory does not exist: " + normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Path is not a directory: " + normalized);
        }

        this.directory = normalized;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.running = false;

        // Register for all three standard event kinds
        this.directory.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        );
    }

    /**
     * Returns the directory being monitored.
     *
     * @return the normalized, absolute monitored directory path
     */
    public Path getDirectory() {
        return directory;
    }

    /**
     * Starts the monitoring loop on the calling thread, dispatching each detected
     * filesystem event to the given handler. This method blocks until {@link #stop()}
     * is called or the calling thread is interrupted.
     *
     * <p>If the WatchService is already closed when this method is entered,
     * or if {@link #stop()} is called concurrently, the loop exits gracefully.
     *
     * @param handler consumer that receives each {@link MonitorEvent}; must not be null
     * @throws IllegalArgumentException if handler is null
     */
    public void start(Consumer<MonitorEvent> handler) {
        Objects.requireNonNull(handler, "Event handler cannot be null");

        running = true;

        while (running) {
            WatchKey key;
            try {
                // take() blocks until an event is available or the service is closed
                key = watchService.take();
            } catch (InterruptedException e) {
                // Restore interrupt status and exit cleanly
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                // stop() was called — exit cleanly
                break;
            }

            List<WatchEvent<?>> events = key.pollEvents();
            for (WatchEvent<?> event : events) {
                WatchEvent.Kind<?> kind = event.kind();

                // OVERFLOW means events were dropped by the OS — skip silently
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                // The context of a directory-watch event is a relative Path (filename)
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path relativeName = pathEvent.context();
                Path fullPath = directory.resolve(relativeName);

                MonitorEventType type = toEventType(kind);
                if (type != null) {
                    handler.accept(new MonitorEvent(fullPath, type));
                }
            }

            // Reset the key to receive further events; if it has become invalid, stop
            boolean valid = key.reset();
            if (!valid) {
                // Directory was deleted or became inaccessible — exit gracefully
                break;
            }
        }
    }

    /**
     * Signals the monitoring loop to stop and closes the underlying {@link WatchService}.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    public void stop() {
        running = false;
        try {
            watchService.close();
        } catch (IOException e) {
            // Best-effort close — ignore secondary errors on shutdown
        }
    }

    /**
     * Implements {@link AutoCloseable} by delegating to {@link #stop()}.
     */
    @Override
    public void close() {
        stop();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link WatchEvent.Kind} to its corresponding {@link MonitorEventType}.
     *
     * @param kind the WatchEvent kind
     * @return the corresponding MonitorEventType, or null for OVERFLOW/unknown kinds
     */
    private static MonitorEventType toEventType(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return MonitorEventType.CREATED;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return MonitorEventType.MODIFIED;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return MonitorEventType.DELETED;
        }
        return null;
    }
}
