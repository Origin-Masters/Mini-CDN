package de.htwsaar.minicdn.edge.infrastructure.persistence;

import de.htwsaar.minicdn.edge.application.config.EdgeRuntimeConfig;
import de.htwsaar.minicdn.edge.domain.model.ReplacementStrategy;
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
 * Dateibasierter Adapter für den persistierten Runtime-Zustand der Edge-Node.
 *
 * <p>Gespeichert werden Konfiguration und TTL-Policies in einer einzelnen
 * {@code .properties}-Datei.</p>
 */
@Component
public class EdgeRuntimeStateStore {

    /** Pfad zur Datei mit dem persistierten Runtime-Zustand. */
    private final Path stateFile;

    /**
     * Erstellt den Adapter mit dem konfigurierten Dateipfad.
     *
     * @param stateFile Pfad zur State-Datei
     */
    public EdgeRuntimeStateStore(
            @Value("${edge.recovery.state-file:data/edge-runtime-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    /**
     * Persistiert Runtime-Konfiguration und TTL-Policies.
     *
     * @param config zu speichernde Konfiguration
     * @param ttlPolicies TTL-Policies nach Präfix, darf {@code null} sein
     * @throws IllegalStateException wenn das Schreiben fehlschlägt
     */
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
            throw new IllegalStateException("Persistieren des Edge-Runtime-Zustands fehlgeschlagen", ex);
        }
    }

    /**
     * Lädt den zuletzt gespeicherten Runtime-Zustand.
     *
     * @return wiederhergestellter Zustand oder {@code null}, falls keine gültigen Daten vorliegen
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
     * Bündelt wiederhergestellte Konfiguration und TTL-Policies.
     *
     * @param config wiederhergestellte Konfiguration
     * @param ttlPolicies wiederhergestellte TTL-Policies
     */
    public record RestoredState(EdgeRuntimeConfig config, Map<String, Long> ttlPolicies) {}

    /**
     * Parst einen String in einen {@code long}-Wert mit Fallback.
     *
     * @param value zu parsende Zeichenkette
     * @param fallback Rückgabewert bei ungültigem Input
     * @return geparster Wert oder Fallback
     */
    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Liest die Properties-Datei des Runtime-Zustands.
     *
     * @return geladene Properties oder {@code null}, wenn keine Datei existiert
     * @throws IOException bei Lesefehlern
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
     * Schreibt die Properties atomar auf die Ziel-Datei.
     *
     * @param props zu speichernde Properties
     * @throws IOException bei Schreib- oder Move-Fehlern
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
