package de.htwsaar.minicdn.edge.infrastructure.persistence;

import de.htwsaar.minicdn.edge.application.config.EdgeRuntimeConfig;
import de.htwsaar.minicdn.edge.infrastructure.cache.ReplacementStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *  persistence for edge runtime state using a .properties file.
 * Format:
 *   region=eu-west
 *   defaultTtlMs=60000
 *   maxEntries=100
 *   replacementStrategy=LRU
 *   ttl.images/=90000
 */
@Component
public class EdgeRuntimeStateStore {

    private final Path stateFile;

    /**
     * Creates a state store backed by the given properties file path.
     *
     * @param stateFile path to the properties file used for persistence */
    public EdgeRuntimeStateStore(
            @Value("${edge.recovery.state-file:data/edge-runtime-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    /**
     * Persists the runtime configuration and TTL policies to the properties file.
     *
     * @param config the runtime configuration to persist
     * @param ttlPolicies map of path prefixes to TTL values in milliseconds; may be {@code null}
     * @throws IllegalStateException if writing to disk fails */
    public synchronized void save(EdgeRuntimeConfig config, Map<String, Long> ttlPolicies) {
        Properties props = new Properties();
        props.setProperty("region", config.region());
        props.setProperty("defaultTtlMs", String.valueOf(config.defaultTtlMs()));
        props.setProperty("maxEntries", String.valueOf(config.maxEntries()));
        props.setProperty("replacementStrategy", config.replacementStrategy().name());
        props.setProperty("originBaseUrl", config.originBaseUrl());

        if (ttlPolicies != null) {
            ttlPolicies.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null) {
                    props.setProperty("ttl." + k.trim(), String.valueOf(v));
                }
            });
        }

        try {
            writeProps(props);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist edge runtime state", ex);
        }
    }

    /**
     * Loads the previously persisted runtime configuration and TTL policies.
     *
     * @return restored state or {@code null} if the file is missing/invalid
     *
     */
    public synchronized RestoredState load() {
        try {
            Properties props = readProps();
            if (props == null) return null;
            String region = props.getProperty("region");
            String repl = props.getProperty("replacementStrategy");
            String originBaseUrl = props.getProperty("originBaseUrl");
            if (region == null || repl == null || originBaseUrl == null || originBaseUrl.isBlank()) {
                return null;
            }

            long ttl = parseLong(props.getProperty("defaultTtlMs"), 60000);
            int max = (int) parseLong(props.getProperty("maxEntries"), 100);
            EdgeRuntimeConfig config = new EdgeRuntimeConfig(
                    region.trim(),
                    Math.max(0, ttl),
                    Math.max(0, max),
                    ReplacementStrategy.valueOf(repl.trim().toUpperCase()),
                    originBaseUrl.trim());

            Map<String, Long> policies = new HashMap<>();
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith("ttl.")) continue;
                String prefix = key.substring("ttl.".length()).trim();
                if (prefix.isBlank()) continue;
                long v = parseLong(props.getProperty(key), 0);
                policies.put(prefix, Math.max(0, v));
            }

            return new RestoredState(config, policies);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Combines the restored runtime configuration and TTL policies.
     *
     * @param config restored runtime configuration * @param ttlPolicies restored TTL policies */
    public record RestoredState(EdgeRuntimeConfig config, Map<String, Long> ttlPolicies) {}

    /**
     * Parses a string to {@code long} with a fallback if invalid.
     *
     * @param value string value to parse * @param fallback value returned on null/blank/parse error * @return parsed long or fallback */
    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Reads properties from the state file.
     *
     * @return loaded properties or {@code null} if file does not exist
     * * @throws IOException if reading fails
     */
    private Properties readProps() throws IOException {
        if (!Files.exists(stateFile)) return null;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        }
        return props;
    }

    /**
     * Writes the given properties atomically to the state file.
     *
     * @param props properties to persist
     * @throws IOException if writing or moving the temp file fails
     */
    private void writeProps(Properties props) throws IOException {
        Path parent = stateFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, "edge runtime state");
        }
        Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
