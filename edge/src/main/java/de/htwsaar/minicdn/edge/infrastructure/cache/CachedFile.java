package de.htwsaar.minicdn.edge.infrastructure.cache;

/**
 * Interner, unveränderlicher Cache-Eintrag.
 *
 * @param body        Datei-Bytes
 * @param contentType MIME-Type (optional)
 * @param sha256      SHA-256 Hex-String
 * @param expiresAtMs Ablaufzeitpunkt in ms seit Epoch
 */
public record CachedFile(byte[] body, String contentType, String sha256, long expiresAtMs) {}
