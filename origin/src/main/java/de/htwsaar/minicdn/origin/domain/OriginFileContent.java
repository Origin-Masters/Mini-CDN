package de.htwsaar.minicdn.origin.domain;

/**
 * Transportneutraler Dateiinhalt inkl. Integritäts-Hash.
 *
 * @param body Dateiinhalt
 * @param contentType Content-Type
 * @param sha256Hex SHA-256 Hex
 */
public record OriginFileContent(byte[] body, String contentType, String sha256Hex) {}
