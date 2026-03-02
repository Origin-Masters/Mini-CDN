package de.htwsaar.minicdn.router.domain;

import java.net.URI;

/**
 * Fachliches Ergebnis des Use-Cases "Datei routen".
 *
 * @param status fachlicher Status
 * @param location Ziel-URI bei erfolgreichem Redirect
 * @param messageId erzeugte Message-ID
 * @param retryCount Anzahl der benötigten Retries
 * @param errorMessage Fehlermeldung bei fachlichem Fehler
 */
public record RouteFileResult(
        RouteStatus status, URI location, String messageId, int retryCount, String errorMessage) {}
