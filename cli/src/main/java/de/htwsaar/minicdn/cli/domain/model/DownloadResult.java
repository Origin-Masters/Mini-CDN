package de.htwsaar.minicdn.cli.domain.model;

/**
 * Ergebnis eines Downloads.
 *
 * @param statusCode HTTP-Status (nur bei HTTP-Antwort)
 * @param bytesWritten geschriebene Bytes (nur bei Erfolg)
 * @param error Fehlertext bei IO/Client-Problemen (kein HTTP-Status)
 */
public record DownloadResult(Integer statusCode, long bytesWritten, String error) {

    /**
     * Erzeugt ein erfolgreiches Download-Ergebnis.
     *
     * @param statusCode HTTP-Statuscode
     * @param bytesWritten Anzahl der geschriebenen Bytes
     * @return erfolgreiches Ergebnis ohne Fehlertext
     */
    public static DownloadResult ok(int statusCode, long bytesWritten) {
        return new DownloadResult(statusCode, bytesWritten, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen HTTP-Fehler nach erfolgreichem Transport.
     *
     * @param statusCode HTTP-Fehlerstatus
     * @return Ergebnis ohne geschriebene Bytes
     */
    public static DownloadResult httpError(int statusCode) {
        return new DownloadResult(statusCode, 0L, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen lokalen IO- oder Transportfehler.
     *
     * @param message Fehlermeldung
     * @return Ergebnis ohne HTTP-Status
     */
    public static DownloadResult ioError(String message) {
        return new DownloadResult(null, 0L, message == null ? "io error" : message);
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
}
