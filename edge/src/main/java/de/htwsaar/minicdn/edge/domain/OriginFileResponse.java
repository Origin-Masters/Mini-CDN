package de.htwsaar.minicdn.edge.domain;

/**
 * Transport-agnostische Antwort vom Origin-Server.
 *
 * @param statusCode  HTTP-Statuscode (z. B. 200, 404)
 * @param body        Datei-Bytes (bei non-2xx {@code null})
 * @param contentType Content-Type Header (optional)
 * @param sha256      X-Content-SHA256 Header (optional)
 */
public record OriginFileResponse(int statusCode, byte[] body, String contentType, String sha256) {}
