package de.htwsaar.minicdn.cli.adapter.out.transport;

import java.util.List;
import java.util.Map;

/**
 * Adapterinterne Beschreibung einer technischen Antwort.
 *
 * <p>Auch dieses Modell bleibt bewusst außerhalb der fachlichen Schicht, weil
 * Statuscodes und Header technische Konzepte der konkreten Bindung sind.</p>
 *
 * @param statusCode technischer Statuscode; bei Transportfehlern {@code null}
 * @param body Text-Body; bei leerem Body ggf. {@code null} oder leer
 * @param headers technische Antwort-Metadaten, normalisiert auf Kleinbuchstaben
 * @param error Fehlertext bei Transport- oder I/O-Fehlern, sonst {@code null}
 */
public record TransportResponse(Integer statusCode, String body, Map<String, List<String>> headers, String error) {

    /**
     * Normalisiert Header auf eine unveränderliche Map; {@code null} wird zu
     * einer leeren Map.
     */
    public TransportResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Erstellt eine erfolgreiche technische Antwort.
     *
     * @param statusCode technischer Statuscode
     * @param body Response-Body
     * @param headers Antwort-Header
     * @return erfolgreiche technische Antwort
     */
    public static TransportResponse success(int statusCode, String body, Map<String, List<String>> headers) {
        return new TransportResponse(statusCode, body, headers, null);
    }

    /**
     * Erstellt eine Fehlerantwort für technische Transport- oder I/O-Fehler.
     *
     * @param message Fehlertext; bei {@code null} wird {@code "io error"} gesetzt
     * @return Antwort ohne Statuscode und Body
     */
    public static TransportResponse ioError(String message) {
        return new TransportResponse(null, null, Map.of(), message == null ? "io error" : message);
    }

    /**
     * Prüft, ob ein erfolgreicher 2xx-Status vorliegt.
     *
     * @return {@code true} bei Status 200 bis 299
     */
    public boolean is2xx() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * Liefert den ersten Header-Wert zu einem Namen.
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
