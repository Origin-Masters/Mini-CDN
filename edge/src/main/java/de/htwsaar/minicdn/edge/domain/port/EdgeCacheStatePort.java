package de.htwsaar.minicdn.edge.domain.port;

import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import java.util.Map;

/**
 * Port für Persistenz und Wiederherstellung von Cache-Snapshots.
 */
public interface EdgeCacheStatePort {

    /**
     * Persistiert einen Cache-Snapshot.
     *
     * @param entries zu speichernde Cache-Einträge
     * @param nowMs aktueller Zeitpunkt in Millisekunden
     */
    void save(Map<String, CacheEntry> entries, long nowMs);

    /**
     * Lädt den zuletzt gespeicherten Cache-Snapshot.
     *
     * @return geladene Cache-Einträge, ggf. leer
     */
    Map<String, CacheEntry> load();
}
