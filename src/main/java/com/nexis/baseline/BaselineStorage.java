package com.nexis.baseline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Handles serialization and deserialization of baseline data to and from JSON files.
 */
public class BaselineStorage {

    private static final int SCHEMA_VERSION = 1;
    private final Gson gson;

    public BaselineStorage() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    }

    /**
     * Persists the collection of baseline entries to a JSON file at the specified destination.
     *
     * @param destination target file path where the baseline should be saved
     * @param entries     collection of baseline entries to serialize
     * @throws IllegalArgumentException if destination or entries is null
     * @throws BaselineStorageException if an I/O error occurs while writing
     */
    public void writeBaseline(Path destination, Collection<BaselineEntry> entries) throws IOException {
        if (destination == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }
        if (entries == null) {
            throw new IllegalArgumentException("Entries collection cannot be null");
        }

        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        List<BaselineEntryDto> dtos = entries.stream()
            .filter(Objects::nonNull)
            .map(e -> new BaselineEntryDto(
                e.filePath().normalize().toString().replace('\\', '/'),
                e.sha256().toLowerCase()
            ))
            .sorted(Comparator.comparing(dto -> dto.filePath))
            .toList();

        BaselineDocument document = new BaselineDocument(SCHEMA_VERSION, dtos);
        String json = gson.toJson(document);

        try {
            Files.writeString(destination, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BaselineStorageException("Failed to write baseline file: " + destination, e);
        }
    }

    /**
     * Loads baseline entries from the specified JSON file.
     *
     * @param source path to the JSON baseline file to load
     * @return list of parsed and validated BaselineEntry instances
     * @throws IllegalArgumentException if source is null
     * @throws BaselineStorageException if the file does not exist, is not a regular file,
     *                                  contains malformed JSON, or has invalid entry fields
     * @throws IOException if an I/O failure occurs
     */
    public List<BaselineEntry> readBaseline(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Source path cannot be null");
        }
        if (!Files.exists(source)) {
            throw new BaselineStorageException("Baseline file does not exist: " + source);
        }
        if (Files.isDirectory(source)) {
            throw new BaselineStorageException("Baseline path is a directory, not a regular file: " + source);
        }
        if (!Files.isRegularFile(source)) {
            throw new BaselineStorageException("Baseline path is not a regular file: " + source);
        }
        if (!Files.isReadable(source)) {
            throw new BaselineStorageException("Baseline file is not readable: " + source);
        }

        String content;
        try {
            content = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BaselineStorageException("Failed to read baseline file: " + source, e);
        }

        if (content == null || content.strip().isEmpty()) {
            return Collections.emptyList();
        }

        BaselineDocument document;
        try {
            document = gson.fromJson(content, BaselineDocument.class);
        } catch (JsonSyntaxException e) {
            throw new BaselineStorageException("Malformed baseline JSON in file: " + source, e);
        }

        if (document == null || document.entries == null) {
            return Collections.emptyList();
        }

        List<BaselineEntry> result = new ArrayList<>();
        for (BaselineEntryDto dto : document.entries) {
            if (dto == null) {
                throw new BaselineStorageException("Null baseline entry encountered in file: " + source);
            }
            if (dto.filePath == null || dto.filePath.isBlank()) {
                throw new BaselineStorageException("Missing or blank 'filePath' in baseline file: " + source);
            }
            if (dto.sha256 == null) {
                throw new BaselineStorageException("Missing 'sha256' for entry '" + dto.filePath + "' in file: " + source);
            }

            try {
                Path path = Path.of(dto.filePath).normalize();
                result.add(new BaselineEntry(path, dto.sha256));
            } catch (IllegalArgumentException e) {
                throw new BaselineStorageException("Invalid baseline entry for path '" + dto.filePath + "': " + e.getMessage(), e);
            }
        }

        return Collections.unmodifiableList(result);
    }

    private static class BaselineDocument {
        @SuppressWarnings("unused") // Used by Gson's reflection during serialization/deserialization
        int version;
        List<BaselineEntryDto> entries;

        @SuppressWarnings("unused")
        BaselineDocument() {}

        BaselineDocument(int version, List<BaselineEntryDto> entries) {
            this.version = version;
            this.entries = entries;
        }
    }

    private static class BaselineEntryDto {
        String filePath;
        String sha256;

        @SuppressWarnings("unused")
        BaselineEntryDto() {}

        BaselineEntryDto(String filePath, String sha256) {
            this.filePath = filePath;
            this.sha256 = sha256;
        }
    }
}
