package de.htwsaar.minicdn.cli.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import de.htwsaar.minicdn.common.util.ExitCodes;
import java.util.Objects;

/**
 * Normierte Antwort des Statistik-Abrufs.
 *
 * @param status fachlicher Ergebnisstatus
 * @param code transportabhängiger Rückgabecode der Gegenstelle; bei technischem Fehler {@code null}
 * @param rawBody roher Antwortinhalt
 * @param jsonData geparste JSON-Daten bei Erfolg
 * @param error technischer oder Parsing-Fehler
 */
public record StatsResponse(ExitCodes status, Integer code, String rawBody, JsonNode jsonData, String error) {

    /**
     * Erzeugt eine erfolgreiche Antwort.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher JSON-Inhalt
     * @param jsonData geparste JSON-Daten
     * @return erfolgreiche Antwort
     */
    public static StatsResponse success(int code, String rawBody, JsonNode jsonData) {
        return new StatsResponse(ExitCodes.SUCCESS, code, rawBody, jsonData, null);
    }

    /**
     * Erzeugt eine fachliche Fehlerantwort der Gegenstelle.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @return Fehlerantwort ohne technischen Fehlertext
     */
    public static StatsResponse rejected(int code, String rawBody) {
        return new StatsResponse(ExitCodes.REJECTED, code, rawBody, null, null);
    }

    /**
     * Erzeugt eine Serverfehlerantwort der Gegenstelle.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @return Fehlerantwort ohne technischen Fehlertext
     */
    public static StatsResponse serverError(int code, String rawBody) {
        return new StatsResponse(ExitCodes.SERVER_ERROR, code, rawBody, null, null);
    }

    /**
     * Erzeugt eine technische Fehlerantwort.
     *
     * @param message Fehlermeldung
     * @return Fehlerantwort
     */
    public static StatsResponse transportError(String message) {
        return new StatsResponse(
                ExitCodes.REQUEST_FAILED, null, null, null, Objects.toString(message, "transport error"));
    }

    /**
     * Erzeugt eine Parsing-Fehlerantwort.
     *
     * @param code Rückgabecode der Gegenstelle
     * @param rawBody roher Antwortinhalt
     * @param message Fehlermeldung
     * @return Fehlerantwort
     */
    public static StatsResponse parsingError(int code, String rawBody, String message) {
        return new StatsResponse(
                ExitCodes.REQUEST_FAILED, code, rawBody, null, Objects.toString(message, "json parsing error"));
    }

    /**
     * Erzeugt eine lokale Client-Fehlerantwort.
     *
     * @param message Fehlermeldung
     * @return Fehlerantwort
     */
    public static StatsResponse clientError(String message) {
        return new StatsResponse(ExitCodes.VALIDATION, 400, Objects.toString(message, "client error"), null, null);
    }

    /**
     * Prüft, ob die Antwort erfolgreich ist und JSON-Daten enthält.
     *
     * @return {@code true}, wenn Status, JSON und Fehlerfreiheit vorliegen
     */
    public boolean isSuccess() {
        return status == ExitCodes.SUCCESS && error == null && jsonData != null;
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
     * Prüft auf Authentifizierungsfehler.
     *
     * @return {@code true} bei fachlicher Ablehnung
     */
    public boolean isRejected() {
        return status == ExitCodes.REJECTED;
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
