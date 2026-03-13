package de.htwsaar.minicdn.edge.domain.model;

import java.util.Objects;

/**
 * Fachlicher Dateiinhalt vom Origin – ohne Transportdetails.
 *
 * @param body        Datei-Bytes
 * @param contentType MIME-Type (optional)
 * @param sha256      SHA-256 Hex-String laut Origin (optional, wird fachlich validiert)
 */
public record OriginContent(byte[] body, String contentType, String sha256) {

    public OriginContent {
        Objects.requireNonNull(body, "body must not be null");
    }
}
