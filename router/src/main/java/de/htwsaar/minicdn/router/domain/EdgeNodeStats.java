package de.htwsaar.minicdn.router.domain;

import java.util.Map;

/**
 * Fachliche Sicht auf Statistiken einer einzelnen Edge-Node.
 *
 * @param cacheHits Anzahl Cache-Hits
 * @param cacheMisses Anzahl Cache-Misses
 * @param filesCached Anzahl aktuell gecachter Dateien
 * @param downloadsByFile Downloads je Datei
 */
public record EdgeNodeStats(long cacheHits, long cacheMisses, long filesCached, Map<String, Long> downloadsByFile) {}
