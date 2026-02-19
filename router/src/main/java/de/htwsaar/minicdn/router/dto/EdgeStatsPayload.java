package de.htwsaar.minicdn.router.dto;

/**
 * JSON-Payload des Edge-Stats-Endpunkts, wie er vom Router eingelesen wird.
 *
 * @param cacheHits Anzahl Cache-Hits
 * @param cacheMisses Anzahl Cache-Misses
 * @param filesCached Anzahl aktuell gecachter Dateien
 */
public record EdgeStatsPayload(long cacheHits, long cacheMisses, long filesCached) {}
