package de.htwsaar.minicdn.edge.infrastructure.cache;

import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import de.htwsaar.minicdn.edge.domain.port.EdgeCache;

/**
 * Infrastrukturinterne Cache-Abstraktion.
 *
 * <p>Erweitert den fachlichen Port {@link EdgeCache} um die konkrete In-Memory-Verwendung
 * innerhalb der Adapter- und Infrastrukturklassen.</p>
 */
public interface CacheStore extends EdgeCache {

    /**
     * Gibt einen frischen Eintrag zurück oder {@code null} wenn abgelaufen/nicht vorhanden.
     *
     * @param key   Cache-Schlüssel
     * @param nowMs aktueller Zeitstempel in ms
     * @return frischer Eintrag oder {@code null}
     */
    @Override
    CacheEntry getFresh(String key, long nowMs);

    /**
     * Speichert einen Eintrag und führt bei Bedarf Eviction durch.
     *
     * @param key        Cache-Schlüssel
     * @param value      zu cachender Eintrag
     * @param maxEntries maximale Einträge (0 = unbegrenzt)
     * @param nowMs      aktueller Zeitstempel in ms
     */
    @Override
    void put(String key, CacheEntry value, int maxEntries, long nowMs);

    /**
     * Entfernt einen einzelnen Eintrag.
     *
     * @param key Cache-Schlüssel
     * @return {@code true} wenn ein Eintrag entfernt wurde
     */
    @Override
    boolean remove(String key);

    /**
     * Entfernt alle Einträge, deren Schlüssel mit {@code prefix} beginnen.
     *
     * @param prefix Schlüssel-Präfix
     * @return Anzahl entfernter Einträge
     */
    @Override
    int removeByPrefix(String prefix);

    /** Leert den gesamten Cache. */
    @Override
    void clear();

    /**
     * Gibt die aktuelle Anzahl der Cache-Einträge zurück.
     *
     * @return Eintragsanzahl
     */
    @Override
    int size();

    /**
     * Snapshot des aktuellen Cache-Inhalts.
     *
     * @return Map aus Cache-Key zu {@link CacheEntry}
     */
    @Override
    java.util.Map<String, CacheEntry> snapshot();
}
