package de.htwsaar.minicdn.cli.transport;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Transport-unabhängige Request-Beschreibung.
 *
 * @param method HTTP-/Transport-Methode (z. B. GET, POST, DELETE, HEAD)
 * @param uri Ziel-URI
 * @param headers Header/Metadaten
 * @param timeout Request-Timeout
 * @param body Text-Body (optional)
 * @param bodyFile Datei-Body für Uploads (optional)
 */
public record TransportRequest(
        String method, URI uri, Map<String, String> headers, Duration timeout, String body, Path bodyFile) {

    public TransportRequest {
        method = Objects.requireNonNull(method, "method").trim().toUpperCase();
        uri = Objects.requireNonNull(uri, "uri");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        timeout = Objects.requireNonNull(timeout, "timeout");

        if (body != null && bodyFile != null) {
            throw new IllegalArgumentException("body and bodyFile are mutually exclusive");
        }
    }

    public static TransportRequest get(URI uri, Duration timeout, Map<String, String> headers) {
        return new TransportRequest("GET", uri, headers, timeout, null, null);
    }

    public static TransportRequest head(URI uri, Duration timeout, Map<String, String> headers) {
        return new TransportRequest("HEAD", uri, headers, timeout, null, null);
    }

    public static TransportRequest delete(URI uri, Duration timeout, Map<String, String> headers) {
        return new TransportRequest("DELETE", uri, headers, timeout, null, null);
    }

    public static TransportRequest postJson(URI uri, Duration timeout, Map<String, String> headers, String body) {
        return new TransportRequest("POST", uri, headers, timeout, body, null);
    }

    public static TransportRequest patchJson(URI uri, Duration timeout, Map<String, String> headers, String body) {
        return new TransportRequest("PATCH", uri, headers, timeout, body, null);
    }

    public static TransportRequest putJson(URI uri, Duration timeout, Map<String, String> headers, String body) {
        return new TransportRequest("PUT", uri, headers, timeout, body, null);
    }

    public static TransportRequest putFile(URI uri, Duration timeout, Map<String, String> headers, Path bodyFile) {
        return new TransportRequest("PUT", uri, headers, timeout, null, bodyFile);
    }
}
