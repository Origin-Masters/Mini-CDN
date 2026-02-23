package de.htwsaar.minicdn.edge.service;

/**
 * Fachliche Exception für Upstream/Origin-Probleme.
 * Wird im Web-Layer in HTTP-Statuscodes gemappt.
 */
public class EdgeUpstreamException extends RuntimeException {

    private final int statusCode;

    /**
     * Erstellt eine neue Upstream-Exception.
     *
     * @param message    Fehlerbeschreibung
     * @param statusCode gewünschter HTTP-Statuscode (z. B. 502)
     */
    public EdgeUpstreamException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Gibt den zugehörigen HTTP-Statuscode zurück.
     *
     * @return HTTP-Statuscode
     */
    public int getStatusCode() {
        return statusCode;
    }
}
