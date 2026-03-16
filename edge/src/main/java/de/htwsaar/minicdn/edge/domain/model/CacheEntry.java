package de.htwsaar.minicdn.edge.domain.model;

/**
 * Fachlicher Cache-Eintrag für die Edge-Logik.
 *
 * @param body Datei-Bytes
 * @param contentType MIME-Type
 * @param sha256 SHA-256 Hex-String
 * @param expiresAtMs absoluter Ablaufzeitpunkt in Millisekunden
 */
public record CacheEntry(byte[] body, String contentType, String sha256, long expiresAtMs) {}
