package de.htwsaar.minicdn.cli.domain.model;

import de.htwsaar.minicdn.common.util.ExitCodes;
import java.util.Objects;

/**
 * Normiertes Ergebnis eines Login-Aufrufs.
 *
 * @param status fachlicher Ergebnisstatus
 * @param user geparster Benutzer bei Erfolg
 * @param code transportabhängiger Rückgabecode der Gegenstelle, sofern vorhanden
 * @param rawBody roher Antwortinhalt
 * @param error technischer oder Parsing-Fehler
 */
public record LoginResult(ExitCodes status, UserResult user, Integer code, String rawBody, String error) {

    /**
     * Erzeugt ein Erfolgsresultat.
     *
     * @param user geparster Benutzer
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @return Erfolgsresultat
     */
    public static LoginResult success(UserResult user, int code, String rawBody) {
        return new LoginResult(ExitCodes.SUCCESS, Objects.requireNonNull(user, "user"), code, rawBody, null);
    }

    /**
     * Erzeugt ein fachliches Fehlerresultat der Gegenstelle.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @return Fehlerresultat ohne technischen Fehlertext
     */
    public static LoginResult rejected(int code, String rawBody) {
        return new LoginResult(ExitCodes.REJECTED, null, code, rawBody, null);
    }

    /**
     * Erzeugt ein fachliches Serverfehlerresultat der Gegenstelle.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @return Serverfehlerresultat ohne technischen Fehlertext
     */
    public static LoginResult serverError(int code, String rawBody) {
        return new LoginResult(ExitCodes.SERVER_ERROR, null, code, rawBody, null);
    }

    /**
     * Erzeugt ein Resultat für einen technischen Fehler.
     *
     * @param error Fehlermeldung
     * @return Fehlerresultat
     */
    public static LoginResult transportError(String error) {
        return new LoginResult(ExitCodes.REQUEST_FAILED, null, null, null, Objects.toString(error, "transport error"));
    }

    /**
     * Erzeugt ein Resultat für einen Parsing-Fehler.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @param error Fehlermeldung
     * @return Fehlerresultat
     */
    public static LoginResult parsingError(int code, String rawBody, String error) {
        return new LoginResult(
                ExitCodes.REQUEST_FAILED, null, code, rawBody, Objects.toString(error, "json parsing error"));
    }

    /**
     * Prüft, ob der Login erfolgreich war.
     *
     * @return {@code true}, wenn Benutzer, erfolgreicher Status und kein Fehler vorliegen
     */
    public boolean isSuccess() {
        return status == ExitCodes.SUCCESS && user != null && error == null;
    }

    /**
     * Prüft auf einen erfolgreichen Statusbereich.
     *
     * @return {@code true} bei fachlich erfolgreichem Ergebnis
     */
    public boolean isRemoteSuccess() {
        return isSuccess();
    }

    /**
     * Prüft auf einen fachlichen Clientfehler.
     *
     * @return {@code true} bei fachlicher Ablehnung
     */
    public boolean isRemoteRejected() {
        return status == ExitCodes.REJECTED;
    }

    /**
     * Prüft, ob der Login fachlich abgelehnt wurde.
     *
     * @return {@code true} bei abgelehntem Login
     */
    public boolean isRejected() {
        return isRemoteRejected();
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
     * Prüft, ob ein technischer oder Parsing-Fehler vorliegt.
     *
     * @return {@code true}, wenn ein Fehlertext vorhanden ist
     */
    public boolean hasError() {
        return error != null && !error.isBlank();
    }

    /**
     * Liefert den numerischen Exit-Code des Ergebnisses.
     *
     * @return numerischer Exit-Code
     */
    public int exitCode() {
        return status.code();
    }
}
