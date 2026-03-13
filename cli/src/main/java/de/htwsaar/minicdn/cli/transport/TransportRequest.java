package de.htwsaar.minicdn.cli.transport;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Transport-unabhängige Request-Beschreibung.
 *
 * @param method Transport-Methode (z. B. GET, POST, DELETE, HEAD)
 * @param uri Ziel-URI
 * @param headers Header/Metadaten; {@code null} wird zu leerer Map normalisiert
 * @param timeout Request-Timeout
 * @param body Text-Body (optional, exklusiv zu {@code bodyFile})
 * @param bodyFile Datei-Body für Uploads (optional, exklusiv zu {@code body})
 */
public record TransportRequest(
        String method, URI uri, Map<String, String> headers, Duration timeout, String body, Path bodyFile) {

    /**
     * Normalisiert und validiert den Request.
     *
     * <p>Die Methode wird auf Großbuchstaben normalisiert, Header werden kopiert,
     * und {@code body} sowie {@code bodyFile} dürfen nicht gleichzeitig gesetzt sein.
     */
    public TransportRequest {
        method = Objects.requireNonNull(method, "method").trim().toUpperCase();
        uri = Objects.requireNonNull(uri, "uri");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        timeout = Objects.requireNonNull(timeout, "timeout");

        if (body != null && bodyFile != null) {
            throw new IllegalArgumentException("body and bodyFile are mutually exclusive");
        }
    }

    /**
     * Erstellt einen GET-Request.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @return neuer GET-Request
     */
    public static TransportRequest get(URI uri, Duration timeout, Map<String, String> headers) {
        return new TransportRequest("GET", uri, headers, timeout, null, null);
    }

    /**
     * Erstellt einen HEAD-Request.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @return neuer HEAD-Request
     */
    public static TransportRequest head(URI uri, Duration timeout, Map<String, String> headers) {
        return new TransportRequest("HEAD", uri, headers, timeout, null, null);
    }

    /**
     * Erstellt einen DELETE-Request.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @return neuer DELETE-Request
     */
    public static TransportRequest delete(URI uri, Duration timeout, Map<String, String> headers) {
        return new TransportRequest("DELETE", uri, headers, timeout, null, null);
    }

    /**
     * Erstellt einen POST-Request mit JSON-Body.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @param body Text-Body
     * @return neuer POST-Request
     */
    public static TransportRequest postJson(URI uri, Duration timeout, Map<String, String> headers, String body) {
        return new TransportRequest("POST", uri, headers, timeout, body, null);
    }

    /**
     * Erstellt einen PATCH-Request mit JSON-Body.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @param body Text-Body
     * @return neuer PATCH-Request
     */
    public static TransportRequest patchJson(URI uri, Duration timeout, Map<String, String> headers, String body) {
        return new TransportRequest("PATCH", uri, headers, timeout, body, null);
    }

    /**
     * Erstellt einen PUT-Request mit JSON-Body.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @param body Text-Body
     * @return neuer PUT-Request
     */
    public static TransportRequest putJson(URI uri, Duration timeout, Map<String, String> headers, String body) {
        return new TransportRequest("PUT", uri, headers, timeout, body, null);
    }

    /**
     * Erstellt einen PUT-Request mit Datei-Body.
     *
     * @param uri Ziel-URI
     * @param timeout Request-Timeout
     * @param headers Header/Metadaten
     * @param bodyFile Datei für den Request-Body
     * @return neuer PUT-Request
     */
    public static TransportRequest putFile(URI uri, Duration timeout, Map<String, String> headers, Path bodyFile) {
        return new TransportRequest("PUT", uri, headers, timeout, null, bodyFile);
    }
}
