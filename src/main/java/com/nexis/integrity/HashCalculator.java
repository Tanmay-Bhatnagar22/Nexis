package com.nexis.integrity;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cryptographic utility component responsible for computing SHA-256 hashes
 * of files using streaming I/O.
 */
public final class HashCalculator {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    private HashCalculator() {
        // Utility class: prevent instantiation
    }

    /**
     * Calculates the SHA-256 cryptographic hash of the specified file using a streaming approach.
     *
     * @param file the path to the regular file to hash
     * @return lowercase hexadecimal string representation of the SHA-256 hash (64 hex characters)
     * @throws IllegalArgumentException if file is null, does not exist, or is not a regular file
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static String calculateSha256(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Target file path cannot be null");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Target file does not exist: " + file);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Target path is a directory, not a regular file: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Target path is not a regular file: " + file);
        }
        if (!Files.isReadable(file)) {
            throw new IOException("Target file is not readable: " + file);
        }

        MessageDigest digest = createDigest();
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream inputStream = Files.newInputStream(file);
             BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {
            int bytesRead;
            while ((bytesRead = bufferedStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Obtains a MessageDigest instance for the SHA-256 algorithm.
     *
     * @return a MessageDigest instance configured for SHA-256
     * @throws IllegalStateException if SHA-256 is unexpectedly unavailable in the runtime
     */
    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm is unavailable", e);
        }
    }
}

