package de.htwsaar.minicdn.edge.cache;

/**
 * Abstraktion des Cache-Stores.
 * Ermöglicht den Austausch von LRU/LFU ohne Anpassung der Fach- oder Controller-Schicht.
 */
public interface CacheStore {

    /**
     * Gibt einen frischen Eintrag zurück oder {@code null} wenn abgelaufen/nicht vorhanden.
     *
     * @param key   Cache-Schlüssel
     * @param nowMs aktueller Zeitstempel in ms
     * @return frischer Eintrag oder {@code null}
     */
    CachedFile getFresh(String key, long nowMs);

    /**
     * Speichert einen Eintrag und führt bei Bedarf Eviction durch.
     *
     * @param key        Cache-Schlüssel
     * @param value      zu cachender Eintrag
     * @param maxEntries maximale Einträge (0 = unbegrenzt)
     * @param nowMs      aktueller Zeitstempel in ms
     */
    void put(String key, CachedFile value, int maxEntries, long nowMs);

    /**
     * Entfernt einen einzelnen Eintrag.
     *
     * @param key Cache-Schlüssel
     * @return {@code true} wenn ein Eintrag entfernt wurde
     */
    boolean remove(String key);

    /**
     * Entfernt alle Einträge, deren Schlüssel mit {@code prefix} beginnen.
     *
     * @param prefix Schlüssel-Präfix
     * @return Anzahl entfernter Einträge
     */
    int removeByPrefix(String prefix);

    /** Leert den gesamten Cache. */
    void clear();

    /**
     * Gibt die aktuelle Anzahl der Cache-Einträge zurück.
     *
     * @return Eintragsanzahl
     */
    int size();
}
