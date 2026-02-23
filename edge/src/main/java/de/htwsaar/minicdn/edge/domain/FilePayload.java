package de.htwsaar.minicdn.edge.domain;

import java.util.Objects;

/**
 * Fachliches Ergebnis einer Datei-Anfrage – ohne HTTP-Framework-Typen.
 *
 * @param path        relativer Dateipfad (Cache-Key)
 * @param body        Datei-Inhalt
 * @param contentType MIME-Type (optional)
 * @param sha256      SHA-256 Hex-String (Integritätsprüfung)
 * @param cache       HIT oder MISS
 */
public record FilePayload(String path, byte[] body, String contentType, String sha256, CacheDecision cache) {

    public FilePayload {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(sha256, "sha256 must not be null");
        Objects.requireNonNull(cache, "cache must not be null");
    }
}
