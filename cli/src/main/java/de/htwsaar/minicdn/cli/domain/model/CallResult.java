package de.htwsaar.minicdn.cli.domain.model;

import de.htwsaar.minicdn.common.util.ExitCodes;

/**
 * Normiertes Ergebnis eines Remote-Aufrufs aus fachlicher Sicht.
 *
 * <p>Die fachnahe Schicht arbeitet nur mit einem allgemeinen Aufrufzustand und
 * optionalem Nutzdaten-Body. Ein konkreter Transport kann zusätzlich einen
 * transportabhängigen Rückgabecode für Diagnosezwecke mitliefern, ohne die
 * fachliche API auf eine konkrete Technik festzulegen.</p>
 *
 * @param status normierter Ausgang des Remote-Aufrufs
 * @param code optionaler transportabhängiger Rückgabecode
 * @param body Response-Body als Text; bei Fehlern ggf. {@code null}
 * @param error technischer oder lokaler Fehlertext; bei regulären Antworten {@code null}
 */
public record CallResult(ExitCodes status, Integer code, String body, String error) {

    /**
     * Erzeugt ein erfolgreich transportiertes Aufrufergebnis.
     *
     * @param code transportabhängiger Rückgabecode der Antwort
     * @param body Response-Body
     * @return normiertes Ergebnis ohne technischen Fehler
     */
    public static CallResult success(int code, String body) {
        return new CallResult(ExitCodes.SUCCESS, code, body, null);
    }

    /**
     * Erzeugt ein fachlich abgelehntes Ergebnis mit optionalem Diagnosecode.
     *
     * @param code transportabhängiger Rückgabecode der Ablehnung
     * @param body Response-Body
     * @return normiertes Ablehnungsergebnis
     */
    public static CallResult rejected(int code, String body) {
        return new CallResult(ExitCodes.REJECTED, code, body, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen Serverfehler der Gegenstelle.
     *
     * @param code transportabhängiger Rückgabecode der Gegenstelle
     * @param body Response-Body
     * @return normiertes Serverfehler-Ergebnis
     */
    public static CallResult serverError(int code, String body) {
        return new CallResult(ExitCodes.SERVER_ERROR, code, body, null);
    }

    /**
     * Erzeugt ein Ergebnis für einen technischen Transportfehler ohne Serverantwort.
     *
     * @param message technische Fehlermeldung
     * @return Ergebnis mit Fehlertext und ohne Rückgabecode
     */
    public static CallResult transportError(String message) {
        return new CallResult(ExitCodes.REQUEST_FAILED, null, null, message == null ? "transport error" : message);
    }

    /**
     * Erzeugt ein lokales Client-Fehlerergebnis, etwa bei Validierungsfehlern vor dem Request.
     *
     * @param message fachliche oder technische Fehlermeldung
     * @return Ergebnis ohne Transportcode
     */
    public static CallResult clientError(String message) {
        return new CallResult(ExitCodes.VALIDATION, 400, null, message == null ? "client error" : message);
    }

    /**
     * Prüft, ob der Remote-Aufruf erfolgreich war.
     *
     * @return {@code true} bei erfolgreicher Remote-Antwort
     */
    public boolean isSuccess() {
        return status == ExitCodes.SUCCESS;
    }

    /**
     * Liefert aus Kompatibilitätsgründen den generischen Erfolgszustand einer Remote-Antwort.
     *
     * @return {@code true} bei erfolgreicher Remote-Antwort
     */
    public boolean isRemoteSuccess() {
        return isSuccess();
    }

    /**
     * Prüft, ob der Remote-Aufruf fachlich abgelehnt wurde.
     *
     * @return {@code true} bei abgelehnter Remote-Antwort
     */
    public boolean isRejected() {
        return status == ExitCodes.REJECTED;
    }

    /**
     * Liefert aus Kompatibilitätsgründen den generischen Ablehnungszustand einer Remote-Antwort.
     *
     * @return {@code true}, wenn der Aufruf fachlich abgelehnt wurde
     */
    public boolean isRemoteRejected() {
        return isRejected();
    }

    /**
     * Prüft, ob ein technischer Transportfehler vorliegt.
     *
     * @return {@code true} bei Verbindungs- oder I/O-Fehlern
     */
    public boolean isTransportError() {
        return status == ExitCodes.REQUEST_FAILED;
    }

    /**
     * Prüft, ob ein lokaler Eingabe- oder Clientfehler vorliegt.
     *
     * @return {@code true} bei lokal erzeugtem Clientfehler
     */
    public boolean isClientError() {
        return status == ExitCodes.VALIDATION;
    }

    /**
     * Prüft, ob die Gegenstelle einen Serverfehler gemeldet hat.
     *
     * @return {@code true} bei fachlich als Serverfehler klassifiziertem Ergebnis
     */
    public boolean isServerError() {
        return status == ExitCodes.SERVER_ERROR;
    }

    /**
     * Liefert den zur Operation passenden Exit-Code.
     *
     * @return numerischer Exit-Code
     */
    public int exitCode() {
        return status.code();
    }

    /**
     * Prüft, ob ein Diagnosecode vom Transport geliefert wurde.
     *
     * @return {@code true}, wenn ein transportabhängiger Rückgabecode vorhanden ist
     */
    public boolean hasRemoteCode() {
        return code != null;
    }
}
