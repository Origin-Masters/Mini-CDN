package de.htwsaar.minicdn.router.adapter;

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
 * Persistenz für den Routing-Index via .properties-Datei.
 * Format:
 *   region.eu-west=http://localhost:8081,http://localhost:8083
 */
@Component
public class RouterRoutingStateStore {

    private final Path stateFile;

    /**
     * Erstellt einen Store mit dem konfigurierten State-File-Pfad.
     *
     * @param stateFile Pfad zur Properties-Datei (Default: data/routing-state.properties)
     */
    public RouterRoutingStateStore(@Value("${cdn.routing.state-file:data/routing-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    /**
     * Persistiert den gegebenen Routing-Status in der Properties-Datei.
     *
     * @param routingState Map von Region zu Liste von Endpoint-URLs
     */
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
     * Lädt den Routing-Status aus der Properties-Datei.
     *
     * @return Map von Region zu Liste von Endpoint-URLs; leer bei fehlender Datei
     */
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
     * Parst eine kommaseparierte Liste von URLs.
     *
     * @param value kommaseparierte URLs
     * @return Liste getrimmter, nicht-leerer URLs
     */
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
