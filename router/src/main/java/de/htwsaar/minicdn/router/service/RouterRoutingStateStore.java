package de.htwsaar.minicdn.router.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *  persistence for routing table using a .properties file.
 * Format:
 *   region.eu-west=http://localhost:8081,http://localhost:8083
 */
@Component
public class RouterRoutingStateStore {

    private final Path stateFile;

    /**
     * Creates a store bound to the configured state file path.
     *
     * @param stateFile path to the properties file (defaults to data/routing-state.properties)
     */
    public RouterRoutingStateStore(@Value("${cdn.routing.state-file:data/routing-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    /**
     * Persists the given routing state to the properties file.
     *
     * @param routingState map of region identifier to a list of endpoint URLs * @throws Exception if state cannot be written */
    public synchronized void save(Map<String, List<String>> routingState) {
        Properties props = new Properties();
        if (routingState != null) {
            routingState.forEach((region, urls) -> {
                if (region == null || region.isBlank() || urls == null || urls.isEmpty()) {
                    return;
                }
                String value = String.join(",", urls);
                props.setProperty("region." + region.trim(), value);
            });
        }

        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "router routing state");
            }
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist routing state", ex);
        }
    }

    /**
     * Loads the routing state from the properties file.
     *
     * @return a map of region identifiers to lists of endpoint URLs; empty if the file is missing or unreadable */
    public synchronized Map<String, List<String>> load() {
        Map<String, List<String>> result = new HashMap<>();
        if (!Files.exists(stateFile)) return result;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException ex) {
            return result;
        }

        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("region.")) {
                continue;
            }
            String region = key.substring("region.".length()).trim();
            if (region.isBlank()) {
                continue;
            }
            List<String> urls = parseUrls(props.getProperty(key, ""));
            if (!urls.isEmpty()) {
                result.put(region, urls);
            }
        }
        return result;
    }

    /**
     * Parses a comma-separated string into a list of URLs.
     *
     * @param value comma-separated URLs * @return list of trimmed, non-blank URLs */
    private static List<String> parseUrls(String value) {
        List<String> urls = new ArrayList<>();
        if (value == null || value.isBlank()) return urls;
        for (String p : value.split(",")) {
            String u = p.trim();
            if (!u.isBlank()) urls.add(u);
        }
        return urls;
    }
}
