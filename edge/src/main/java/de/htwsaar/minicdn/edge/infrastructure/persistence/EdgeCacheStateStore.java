package de.htwsaar.minicdn.edge.infrastructure.persistence;

import de.htwsaar.minicdn.edge.infrastructure.cache.CachedFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simple file-based persistence for edge cache entries.
 * Everything is stored in one .properties file.
 */
@Component
public class EdgeCacheStateStore {

    /** Absolute or relative path to the persisted cache-state properties file. */
    private final Path stateFile;

    /**
     * Creates the state store using a configurable file path.
     *
     * <p>Default: {@code data/edge-cache-state.properties}
     */
    public EdgeCacheStateStore(@Value("${edge.cache.state-file:data/edge-cache-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    /**
     * Persists the current in-memory cache snapshot to disk.
     *
     * <p>Only valid, non-expired entries are written.
     */
    public synchronized void save(Map<String, CachedFile> entries, long nowMs) {
        // Flat key/value representation that can be written to a single file.
        Properties props = new Properties();
        // Running index for keys like entry.0.*, entry.1.*, ...
        int index = 0;

        if (entries != null) {
            for (Map.Entry<String, CachedFile> entry : entries.entrySet()) {
                String key = entry.getKey();
                CachedFile file = entry.getValue();
                // Skip invalid data to keep the persisted file clean.
                if (isBlank(key) || file == null || file.body() == null) continue;
                // Do not persist already expired entries.
                if (file.expiresAtMs() <= nowMs) continue;

                String prefix = "entry." + index + ".";
                // Logical cache key (e.g. file path).
                props.setProperty(prefix + "key", key);
                // Binary body is stored as Base64 text in the properties file.
                props.setProperty(prefix + "bodyBase64", Base64.getEncoder().encodeToString(file.body()));
                // Optional metadata fields.
                if (!isBlank(file.contentType())) props.setProperty(prefix + "contentType", file.contentType());
                if (!isBlank(file.sha256())) props.setProperty(prefix + "sha256", file.sha256());
                // Absolute expiry timestamp in epoch milliseconds.
                props.setProperty(prefix + "expiresAtMs", Long.toString(file.expiresAtMs()));
                index++;
            }
        }

        try {
            // Ensure target directory exists.
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            // Write to temp file first, then atomically replace target file.
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "edge cache state");
            }
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist cache state", ex);
        }
    }

    /**
     * Loads previously persisted cache entries from disk.
     *
     * <p>Broken entries are ignored so one bad record does not block recovery.
     */
    public synchronized Map<String, CachedFile> load() {
        // No state file means "nothing to recover".
        if (!Files.exists(stateFile)) return Map.of();

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException ex) {
            // I/O problems should not break app startup; recover with empty state.
            return Map.of();
        }

        Map<String, CachedFile> out = new HashMap<>();

        // Iterate over all keys and pick the "entry.N.key" records.
        for (String propName : props.stringPropertyNames()) {
            if (!propName.startsWith("entry.") || !propName.endsWith(".key")) continue;

            String prefix = propName.substring(0, propName.length() - "key".length());
            String key = props.getProperty(propName);
            String encodedBody = props.getProperty(prefix + "bodyBase64");
            // Key and body are mandatory fields.
            if (isBlank(key) || isBlank(encodedBody)) continue;

            try {
                // Rebuild the original CachedFile from persisted text values.
                byte[] body = Base64.getDecoder().decode(encodedBody);
                long expiresAtMs = Long.parseLong(props.getProperty(prefix + "expiresAtMs", "0"));
                String contentType = props.getProperty(prefix + "contentType");
                String sha256 = props.getProperty(prefix + "sha256");
                out.put(key, new CachedFile(body, contentType, sha256, expiresAtMs));
            } catch (RuntimeException ex) {
                // Ignore broken entries and keep loading the rest.
            }
        }

        return out;
    }

    /** Small utility used throughout save/load to validate text values. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
