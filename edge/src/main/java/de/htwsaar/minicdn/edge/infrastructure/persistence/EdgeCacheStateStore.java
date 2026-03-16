package de.htwsaar.minicdn.edge.infrastructure.persistence;

import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import de.htwsaar.minicdn.edge.domain.port.EdgeCacheStatePort;
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
 * Dateibasierter Adapter zur Persistenz von Cache-Einträgen.
 *
 * <p>Alle Einträge werden in einer einzelnen {@code .properties}-Datei gespeichert.</p>
 */
@Component
public class EdgeCacheStateStore implements EdgeCacheStatePort {

    /** Pfad zur persistierten Cache-State-Datei. */
    private final Path stateFile;

    /**
     * Erstellt den Adapter mit einem konfigurierbaren Dateipfad.
     *
     * @param stateFile Pfad zur Cache-State-Datei
     */
    public EdgeCacheStateStore(@Value("${edge.cache.state-file:data/edge-cache-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    /**
     * Persistiert den aktuellen Cache-Snapshot.
     *
     * @param entries Cache-Einträge
     * @param nowMs aktueller Zeitpunkt in Millisekunden
     */
    @Override
    public synchronized void save(Map<String, CacheEntry> entries, long nowMs) {
        // Flache Schlüssel/Wert-Repräsentation für eine einzelne Properties-Datei.
        Properties props = new Properties();
        // Laufender Index für Schlüssel wie entry.0.*, entry.1.*, ...
        int index = 0;

        if (entries != null) {
            for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
                String key = entry.getKey();
                CacheEntry file = entry.getValue();
                // Ungültige Daten werden übersprungen, damit die Datei konsistent bleibt.
                if (isBlank(key) || file == null || file.body() == null) continue;
                // Bereits abgelaufene Einträge werden nicht gespeichert.
                if (file.expiresAtMs() <= nowMs) continue;

                String prefix = "entry." + index + ".";
                // Fachlicher Cache-Schlüssel, z. B. ein Dateipfad.
                props.setProperty(prefix + "key", key);
                // Binärdaten werden Base64-kodiert abgelegt.
                props.setProperty(prefix + "bodyBase64", Base64.getEncoder().encodeToString(file.body()));
                // Optionale Metadaten.
                if (!isBlank(file.contentType())) props.setProperty(prefix + "contentType", file.contentType());
                if (!isBlank(file.sha256())) props.setProperty(prefix + "sha256", file.sha256());
                // Absoluter Ablaufzeitpunkt in Millisekunden seit Epoch.
                props.setProperty(prefix + "expiresAtMs", Long.toString(file.expiresAtMs()));
                index++;
            }
        }

        try {
            // Zielverzeichnis bei Bedarf anlegen.
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            // Zuerst temporär schreiben, dann atomar ersetzen.
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "edge cache state");
            }
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new IllegalStateException("Persistieren des Cache-Zustands fehlgeschlagen", ex);
        }
    }

    /**
     * Lädt zuvor gespeicherte Cache-Einträge.
     *
     * @return geladene Cache-Einträge, ggf. leer
     */
    @Override
    public synchronized Map<String, CacheEntry> load() {
        // Fehlende Datei bedeutet: nichts wiederherzustellen.
        if (!Files.exists(stateFile)) return Map.of();

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException ex) {
            // I/O-Probleme dürfen den Start nicht blockieren.
            return Map.of();
        }

        Map<String, CacheEntry> out = new HashMap<>();

        // Nur Einträge im Schema entry.N.key berücksichtigen.
        for (String propName : props.stringPropertyNames()) {
            if (!propName.startsWith("entry.") || !propName.endsWith(".key")) continue;

            String prefix = propName.substring(0, propName.length() - "key".length());
            String key = props.getProperty(propName);
            String encodedBody = props.getProperty(prefix + "bodyBase64");
            // Schlüssel und Body sind Pflichtfelder.
            if (isBlank(key) || isBlank(encodedBody)) continue;

            try {
                // Cache-Eintrag aus den gespeicherten Textwerten rekonstruieren.
                byte[] body = Base64.getDecoder().decode(encodedBody);
                long expiresAtMs = Long.parseLong(props.getProperty(prefix + "expiresAtMs", "0"));
                String contentType = props.getProperty(prefix + "contentType");
                String sha256 = props.getProperty(prefix + "sha256");
                out.put(key, new CacheEntry(body, contentType, sha256, expiresAtMs));
            } catch (RuntimeException ex) {
                // Defekte Einzel-Einträge werden ignoriert.
            }
        }

        return out;
    }

    /** Prüft, ob ein Textwert leer oder nur aus Whitespace besteht. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
