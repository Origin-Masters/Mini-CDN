package de.htwsaar.minicdn.cli.domain.model;

import de.htwsaar.minicdn.common.util.ExitCodes;

/**
 * Ergebnis eines Downloads.
 *
 * @param status fachlicher Verarbeitungsstatus
 * @param code transportabhängiger Rückgabecode (optional)
 * @param bytesWritten geschriebene Bytes (nur bei Erfolg)
 * @param error Fehlertext bei IO/Client-Problemen (kein Rückgabecode)
 */
public record DownloadResult(ExitCodes status, Integer code, long bytesWritten, String error) {

    /**
     * Erzeugt ein erfolgreiches Download-Ergebnis.
     *
     * @param code transportabhängiger Rückgabecode
     * @param bytesWritten Anzahl der geschriebenen Bytes
     * @return erfolgreiches Ergebnis ohne Fehlertext
     */
    public static DownloadResult success(int code, long bytesWritten) {
        return new DownloadResult(ExitCodes.SUCCESS, code, bytesWritten, null);
    }

    /**
     * Erzeugt ein Ergebnis für eine fachlich abgelehnte Remote-Antwort.
     *
     * @param code transportabhängiger Fehlercode
     * @return Ergebnis ohne geschriebene Bytes
     */
    public static DownloadResult rejected(int code) {
        return new DownloadResult(ExitCodes.REJECTED, code, 0L, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen Serverfehler der Gegenstelle.
     *
     * @param code transportabhängiger Fehlercode
     * @return Ergebnis ohne geschriebene Bytes
     */
    public static DownloadResult serverError(int code) {
        return new DownloadResult(ExitCodes.SERVER_ERROR, code, 0L, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen lokalen IO- oder Transportfehler.
     *
     * @param message Fehlermeldung
     * @return Ergebnis ohne Rückgabecode
     */
    public static DownloadResult ioError(String message) {
        return new DownloadResult(ExitCodes.REQUEST_FAILED, null, 0L, message == null ? "io error" : message);
    }

    /**
     * Prüft, ob der Download fachlich erfolgreich abgeschlossen wurde.
     *
     * @return {@code true} bei erfolgreichem Abschluss
     */
    public boolean isSuccess() {
        return status == ExitCodes.SUCCESS;
    }

    /**
     * Liefert aus Kompatibilitätsgründen den generischen Erfolgszustand einer Remote-Antwort.
     *
     * @return {@code true} bei erfolgreichem Download
     */
    public boolean isRemoteSuccess() {
        return isSuccess();
    }

    /**
     * Liefert aus Kompatibilitätsgründen den bisherigen Erfolgszustand.
     *
     * @return {@code true} bei erfolgreichem Download
     */
    public boolean isSuccessful() {
        return isSuccess();
    }

    /**
     * Prüft, ob ein fachlicher Ablehnungsfall vorliegt.
     *
     * @return {@code true} bei Ablehnung
     */
    public boolean isRejected() {
        return status == ExitCodes.REJECTED;
    }

    /**
     * Liefert aus Kompatibilitätsgründen den generischen Ablehnungszustand einer Remote-Antwort.
     *
     * @return {@code true} bei Ablehnung
     */
    public boolean isRemoteRejected() {
        return isRejected();
    }

    /**
     * Prüft, ob die Gegenstelle einen Serverfehler gemeldet hat.
     *
     * @return {@code true} bei Serverfehler
     */
    public boolean isServerError() {
        return status == ExitCodes.SERVER_ERROR;
    }

    /**
     * Prüft, ob ein technischer Request-Fehler vorliegt.
     *
     * @return {@code true} bei technischem Fehler
     */
    public boolean isRequestFailed() {
        return status == ExitCodes.REQUEST_FAILED;
    }

    /**
     * Liefert den zum Ergebnis passenden Exit-Code.
     *
     * @return numerischer Exit-Code
     */
    public int exitCode() {
        return status.code();
    }
}
