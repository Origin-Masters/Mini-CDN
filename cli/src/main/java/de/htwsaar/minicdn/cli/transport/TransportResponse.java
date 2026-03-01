package de.htwsaar.minicdn.cli.transport;

import java.util.List;
import java.util.Map;

/**
 * Transport-unabhängige Response-Beschreibung.
 *
 * @param statusCode Statuscode der Antwort; bei IO-Fehlern null
 * @param body Text-Body; kann bei HEAD/leerem Body null oder leer sein
 * @param headers Response-Header
 * @param error Fehlertext bei Transport-/IO-Fehlern
 */
public record TransportResponse(Integer statusCode, String body, Map<String, List<String>> headers, String error) {

    public TransportResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static TransportResponse success(int statusCode, String body, Map<String, List<String>> headers) {
        return new TransportResponse(statusCode, body, headers, null);
    }

    public static TransportResponse ioError(String message) {
        return new TransportResponse(null, null, Map.of(), message == null ? "io error" : message);
    }

    public boolean is2xx() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    public String firstHeader(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        List<String> values = headers.get(name.toLowerCase());
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
