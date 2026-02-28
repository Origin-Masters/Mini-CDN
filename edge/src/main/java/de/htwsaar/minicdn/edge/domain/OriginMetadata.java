package de.htwsaar.minicdn.edge.domain;

/**
 * Fachliche Metadaten einer Datei vom Origin – ohne Transportdetails.
 *
 * @param contentType MIME-Type (optional)
 * @param sha256      SHA-256 Hex-String (optional, wird fachlich validiert)
 */
public record OriginMetadata(String contentType, String sha256) {}
