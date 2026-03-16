package de.htwsaar.minicdn.edge.domain.port;

import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import java.util.Map;

/**
 * Fachlicher Port für Cache-Zugriffe der Edge-Logik.
 */
public interface EdgeCache {

    /**
     * Gibt einen nicht abgelaufenen Eintrag zurück.
     *
     * @param key Cache-Schlüssel
     * @param nowMs aktueller Zeitpunkt in Millisekunden
     * @return Cache-Eintrag oder {@code null}, falls kein gültiger Eintrag vorliegt
     */
    CacheEntry getFresh(String key, long nowMs);

    /**
     * Speichert oder überschreibt einen Cache-Eintrag.
     *
     * @param key Cache-Schlüssel
     * @param value zu speichernder Eintrag
     * @param maxEntries maximale Anzahl Einträge, {@code 0} bedeutet unbegrenzt
     * @param nowMs aktueller Zeitpunkt in Millisekunden
     */
    void put(String key, CacheEntry value, int maxEntries, long nowMs);

    /**
     * Entfernt genau einen Cache-Eintrag.
     *
     * @param key Cache-Schlüssel
     * @return {@code true}, wenn ein Eintrag entfernt wurde
     */
    boolean remove(String key);

    /**
     * Entfernt alle Einträge unterhalb eines Präfixes.
     *
     * @param prefix Präfix des Cache-Schlüssels
     * @return Anzahl entfernter Einträge
     */
    int removeByPrefix(String prefix);

    /** Leert den gesamten Cache. */
    void clear();

    /**
     * Liefert die aktuelle Anzahl gespeicherter Einträge.
     *
     * @return Anzahl der Cache-Einträge
     */
    int size();

    /**
     * Erstellt eine unveränderliche Momentaufnahme des aktuellen Cache-Inhalts.
     *
     * @return Snapshot des Cache-Inhalts
     */
    Map<String, CacheEntry> snapshot();
}
