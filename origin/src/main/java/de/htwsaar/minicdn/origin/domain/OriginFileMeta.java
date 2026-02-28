package de.htwsaar.minicdn.origin.domain;

/**
 * Transportneutrale Metadaten einer Datei im Origin.
 *
 * @param path relativer Pfad (Slash-normalisiert)
 * @param sizeBytes Größe in Bytes
 * @param lastModified ISO-8601 Instant String
 * @param contentType MIME-Type (Fallback application/octet-stream)
 */
public record OriginFileMeta(String path, long sizeBytes, String lastModified, String contentType) {}
