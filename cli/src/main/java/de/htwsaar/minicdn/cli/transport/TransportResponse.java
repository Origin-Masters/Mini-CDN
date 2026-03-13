package de.htwsaar.minicdn.cli.transport;

import java.util.List;
import java.util.Map;

/**
 * Transport-unabhängige Response-Beschreibung.
 *
 * @param statusCode Statuscode der Antwort; bei Transportfehlern {@code null}
 * @param body Text-Body; bei leerem Body ggf. {@code null} oder leer
 * @param headers Response-Header (normalisiert auf Kleinbuchstaben)
 * @param error Fehlertext bei Transport-/IO-Fehlern, sonst {@code null}
 */
public record TransportResponse(Integer statusCode, String body, Map<String, List<String>> headers, String error) {

    /**
     * Normalisiert Header auf eine unveränderliche Map; {@code null} wird zu leerer Map.
     */
    public TransportResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Erstellt eine erfolgreiche Antwort.
     *
     * @param statusCode HTTP-Statuscode
     * @param body Response-Body
     * @param headers Response-Header
     * @return erfolgreiche Transport-Antwort
     */
    public static TransportResponse success(int statusCode, String body, Map<String, List<String>> headers) {
        return new TransportResponse(statusCode, body, headers, null);
    }

    /**
     * Erstellt eine Fehlerantwort für technische Transport-/IO-Fehler.
     *
     * @param message Fehlertext; bei {@code null} wird {@code "io error"} gesetzt
     * @return Antwort ohne Statuscode und Body
     */
    public static TransportResponse ioError(String message) {
        return new TransportResponse(null, null, Map.of(), message == null ? "io error" : message);
    }

    /**
     * Prüft, ob ein 2xx-Status vorliegt.
     *
     * @return {@code true} bei Status 200-299, sonst {@code false}
     */
    public boolean is2xx() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * Liefert den ersten Header-Wert zu einem Namen.
     *
     * <p>Der Name wird intern in Kleinbuchstaben aufgelöst. Ist der Name leer,
     * fehlt der Header oder hat keine Werte, wird {@code null} zurückgegeben.
     *
     * @param name Header-Name
     * @return erster Header-Wert oder {@code null}
     */
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
