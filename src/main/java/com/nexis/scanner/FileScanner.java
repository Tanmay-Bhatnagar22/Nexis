package com.nexis.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reusable file scanner component responsible for recursively discovering regular files
 * within a specified directory using Java NIO.
 */
public class FileScanner {

    /**
     * Recursively scans the provided root directory and returns a sorted list of regular files.
     *
     * @param root the root directory to scan
     * @return a sorted, deterministic list of Path objects representing regular files
     * @throws IllegalArgumentException if root is null, does not exist, is not a directory, or is inaccessible
     * @throws IOException if an I/O error occurs during directory traversal
     */
    public List<Path> scan(Path root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Scan path cannot be null");
        }
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Scan path does not exist: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Scan path is not a directory: " + root);
        }
        if (!Files.isReadable(root)) {
            throw new IllegalArgumentException("Scan path is not accessible: " + root);
        }

        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(this::isRegularFile)
                .sorted()
                .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Determines whether the given path represents a regular file without following symbolic links.
     * Gracefully handles security exceptions.
     *
     * @param path the path to check
     * @return true if the path is a regular file, false otherwise
     */
    private boolean isRegularFile(Path path) {
        try {
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        } catch (SecurityException e) {
            return false;
        }
    }
}

