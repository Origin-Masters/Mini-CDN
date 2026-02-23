package de.htwsaar.minicdn.router.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * JSON-Payload des Edge-Stats-Endpunkts, wie er vom Router eingelesen wird.
 *
 * @param cacheHits Anzahl Cache-Hits
 * @param cacheMisses Anzahl Cache-Misses
 * @param filesCached Anzahl aktuell gecachter Dateien
 * @param downloadsByFile Download-Anzahl je Datei auf einer Edge-Node
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeStatsPayload(long cacheHits, long cacheMisses, long filesCached, Map<String, Long> downloadsByFile) {}
