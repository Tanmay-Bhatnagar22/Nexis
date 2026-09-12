package com.nexis.monitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link DirectoryMonitor}.
 *
 * <p>Event-detection tests run the monitor on a background thread, perform the
 * filesystem operation on the test thread, then wait up to 5 seconds for the
 * event via a {@link CountDownLatch} - no arbitrary {@code Thread.sleep()} calls.
 *
 * <p>All {@link DirectoryMonitor} instances are opened in try-with-resources blocks
 * so the underlying WatchService is always released, even if a test fails or throws.
 */
class DirectoryMonitorTest {

    /** Maximum milliseconds to wait for a WatchService event before failing. */
    private static final long EVENT_TIMEOUT_MS = 5000;

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // 1. Valid directory - monitor starts without exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("1. Monitor starts successfully on a valid directory")
    void monitorStartsOnValidDirectory() throws IOException {
        try (DirectoryMonitor monitor = new DirectoryMonitor(tempDir)) {
            assertNotNull(monitor, "DirectoryMonitor should be created without error");
            assertEquals(tempDir.toAbsolutePath().normalize(), monitor.getDirectory());
        } // close() = stop() - WatchService released here
    }

    // -------------------------------------------------------------------------
    // 2. CREATE event is detected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("2. CREATE event is detected when a new file appears")
    void createEventIsDetected() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<MonitorEvent> received = new AtomicReference<>();

        try (DirectoryMonitor monitor = new DirectoryMonitor(tempDir)) {

            Thread monitorThread = new Thread(() -> monitor.start(event -> {
                if (event.eventType() == MonitorEventType.CREATED) {
                    received.set(event);
                    latch.countDown();
                }
            }));
            monitorThread.setDaemon(true);
            monitorThread.start();

            // Give the WatchService a moment to arm before the filesystem operation
            Thread.sleep(200);

            Path newFile = tempDir.resolve("created.txt");
            Files.writeString(newFile, "hello", StandardCharsets.UTF_8);

            boolean signalled = latch.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            monitor.stop(); // unblocks monitorThread; close() in finally is then a no-op
            monitorThread.join(1000);

            assertTrue(signalled, "Should receive CREATE event within timeout");
            assertNotNull(received.get(), "MonitorEvent should not be null");
            assertEquals(MonitorEventType.CREATED, received.get().eventType());
            assertEquals(newFile.toAbsolutePath().normalize(), received.get().filePath());
        }
    }

    // -------------------------------------------------------------------------
    // 3. MODIFY event is detected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3. MODIFY event is detected when an existing file is modified")
    void modifyEventIsDetected() throws Exception {
        // Pre-create the file before starting the monitor
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "original", StandardCharsets.UTF_8);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<MonitorEvent> received = new AtomicReference<>();

        try (DirectoryMonitor monitor = new DirectoryMonitor(tempDir)) {

            Thread monitorThread = new Thread(() -> monitor.start(event -> {
                if (event.eventType() == MonitorEventType.MODIFIED) {
                    received.set(event);
                    latch.countDown();
                }
            }));
            monitorThread.setDaemon(true);
            monitorThread.start();

            Thread.sleep(200);

            Files.writeString(file, "modified content", StandardCharsets.UTF_8);

            boolean signalled = latch.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            monitor.stop();
            monitorThread.join(1000);

            assertTrue(signalled, "Should receive MODIFY event within timeout");
            assertNotNull(received.get(), "MonitorEvent should not be null");
            assertEquals(MonitorEventType.MODIFIED, received.get().eventType());
            assertEquals(file.toAbsolutePath().normalize(), received.get().filePath());
        }
    }

    // -------------------------------------------------------------------------
    // 4. DELETE event is detected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("4. DELETE event is detected when a file is removed")
    void deleteEventIsDetected() throws Exception {
        Path file = tempDir.resolve("to_delete.txt");
        Files.writeString(file, "goodbye", StandardCharsets.UTF_8);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<MonitorEvent> received = new AtomicReference<>();

        try (DirectoryMonitor monitor = new DirectoryMonitor(tempDir)) {

            Thread monitorThread = new Thread(() -> monitor.start(event -> {
                if (event.eventType() == MonitorEventType.DELETED) {
                    received.set(event);
                    latch.countDown();
                }
            }));
            monitorThread.setDaemon(true);
            monitorThread.start();

            Thread.sleep(200);

            Files.delete(file);

            boolean signalled = latch.await(EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            monitor.stop();
            monitorThread.join(1000);

            assertTrue(signalled, "Should receive DELETE event within timeout");
            assertNotNull(received.get(), "MonitorEvent should not be null");
            assertEquals(MonitorEventType.DELETED, received.get().eventType());
            assertEquals(file.toAbsolutePath().normalize(), received.get().filePath());
        }
    }

    // -------------------------------------------------------------------------
    // 5. Invalid path (non-directory) is rejected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("5. Constructor rejects a path that is a regular file")
    void constructorRejectsFilePath() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not_a_dir.txt"));
        // Assign return value so the compiler sees it is used (avoids "result ignored" warning)
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new DirectoryMonitor(file),
            "Should throw IllegalArgumentException for a file path"
        );
        assertNotNull(ex.getMessage(), "Exception should carry a descriptive message");
    }

    @Test
    @DisplayName("5b. Constructor rejects a path that does not exist")
    void constructorRejectsNonexistentPath() {
        Path missing = tempDir.resolve("does_not_exist");
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new DirectoryMonitor(missing),
            "Should throw IllegalArgumentException for a non-existent path"
        );
        assertNotNull(ex.getMessage(), "Exception should carry a descriptive message");
    }

    @Test
    @DisplayName("5c. Constructor rejects a null path")
    void constructorRejectsNullPath() {
        NullPointerException ex = assertThrows(
            NullPointerException.class,
            () -> new DirectoryMonitor(null),
            "Should throw NullPointerException for null path"
        );
        assertNotNull(ex, "Exception should be non-null");
    }

    // -------------------------------------------------------------------------
    // 6. Watcher shuts down cleanly
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("6. stop() causes start() to return and the monitor thread exits")
    void monitorShutsDownCleanly() throws Exception {
        CountDownLatch started = new CountDownLatch(1);

        try (DirectoryMonitor monitor = new DirectoryMonitor(tempDir)) {

            Thread monitorThread = new Thread(() -> {
                started.countDown();
                monitor.start(event -> { /* no-op */ });
            });
            monitorThread.setDaemon(true);
            monitorThread.start();

            // Wait until the monitor loop is running
            assertTrue(started.await(2000, TimeUnit.MILLISECONDS), "Monitor should start quickly");

            // Let the WatchService fully arm
            Thread.sleep(100);

            monitor.stop();
            monitorThread.join(3000);

            assertTrue(!monitorThread.isAlive(), "Monitor thread should have terminated after stop()");
        }
    }

    // -------------------------------------------------------------------------
    // 7. Calling stop() twice does not crash
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7. Calling stop() twice is safe (no exception)")
    void doubleStopIsSafe() throws IOException {
        try (DirectoryMonitor monitor = new DirectoryMonitor(tempDir)) {
            monitor.stop(); // first explicit stop
        }
        // try-with-resources calls close() -> stop() a second time - must not throw
    }
}
