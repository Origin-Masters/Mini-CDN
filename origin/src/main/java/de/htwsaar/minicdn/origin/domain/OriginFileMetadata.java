package de.htwsaar.minicdn.origin.domain;

/**
 * Transportneutrale Dateimetadaten.
 *
 * @param contentType Content-Type
 * @param lengthBytes Content-Length in Bytes
 * @param sha256Hex SHA-256 Hex
 */
public record OriginFileMetadata(String contentType, long lengthBytes, String sha256Hex) {}
