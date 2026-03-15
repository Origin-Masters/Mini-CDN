package de.htwsaar.minicdn.cli.domain.model;

/**
 * Normiertes Ergebnis eines textbasierten Remote-Aufrufs.
 *
 * @param statusCode HTTP-Statuscode, sofern eine Serverantwort vorliegt
 * @param body Response-Body als Text; bei Fehlern ggf. {@code null}
 * @param error technischer oder lokaler Fehlertext; bei regulären HTTP-Antworten {@code null}
 */
public record CallResult(Integer statusCode, String body, String error) {

    /**
     * Erzeugt ein erfolgreich transportiertes Aufrufergebnis.
     *
     * @param statusCode HTTP-Statuscode der Antwort
     * @param body Response-Body
     * @return normiertes Ergebnis ohne technischen Fehler
     */
    public static CallResult success(int statusCode, String body) {
        return new CallResult(statusCode, body, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen technischen Transportfehler ohne HTTP-Antwort.
     *
     * @param message technische Fehlermeldung
     * @return Ergebnis mit Fehlertext und ohne Statuscode
     */
    public static CallResult transportError(String message) {
        return new CallResult(null, null, message == null ? "transport error" : message);
    }

    /**
     * Erzeugt ein lokales Client-Fehlerergebnis, etwa bei Validierungsfehlern vor dem Request.
     *
     * @param message fachliche oder technische Fehlermeldung
     * @return Ergebnis mit synthetischem 400er-Status
     */
    public static CallResult clientError(String message) {
        return new CallResult(400, null, message);
    }

    /**
     * Prüft, ob ein 2xx-Status vorliegt.
     *
     * @return {@code true} bei Status 200 bis 299
     */
    public boolean is2xx() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    /**
     * Prüft, ob ein 4xx-Status vorliegt.
     *
     * @return {@code true} bei Status 400 bis 499
     */
    public boolean is4xx() {
        return statusCode != null && statusCode >= 400 && statusCode < 500;
    }

    /**
     * Prüft, ob ein 5xx-Status vorliegt.
     *
     * @return {@code true} bei Status 500 bis 599
     */
    public boolean is5xx() {
        return statusCode != null && statusCode >= 500 && statusCode < 600;
    }
}
