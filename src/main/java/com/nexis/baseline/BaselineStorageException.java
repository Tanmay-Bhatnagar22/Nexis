package com.nexis.baseline;

import java.io.IOException;

/**
 * Exception thrown when an error occurs during baseline serialization,
 * deserialization, or baseline file storage operations.
 */
public class BaselineStorageException extends IOException {

    public BaselineStorageException(String message) {
        super(message);
    }

    public BaselineStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

