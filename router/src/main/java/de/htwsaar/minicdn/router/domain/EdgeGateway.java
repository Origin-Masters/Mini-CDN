package de.htwsaar.minicdn.router.domain;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Port für die fachliche Kommunikation mit Edge-Knoten.
 *
 * <p>Die Fachlogik kennt nur fachliche Operationen und keine
 * HTTP-spezifischen Details wie Endpunkte, Verben oder Statuscodes.</p>
 */
public interface EdgeGateway {

    /**
     * Prüft, ob ein Edge-Knoten grundsätzlich erreichbar ist.
     *
     * @param node Edge-Knoten
     * @param timeout Timeout für den Check
     * @return {@code true}, wenn der Knoten erreichbar ist
     */
    boolean isNodeResponsive(EdgeNode node, Duration timeout);

    /**
     * Führt einen asynchronen Health-Check aus.
     *
     * @param node Edge-Knoten
     * @param timeout Timeout für den Check
     * @return Future mit {@code true}, wenn der Knoten gesund ist
     */
    CompletableFuture<Boolean> checkNodeHealth(EdgeNode node, Duration timeout);

    /**
     * Lädt Admin-Statistiken eines Edge-Knotens.
     *
     * @param node Edge-Knoten
     * @param windowSec Zeitfenster in Sekunden
     * @param timeout Request-Timeout
     * @return fachliche Sicht auf die Edge-Statistiken
     * @throws Exception bei Kommunikations- oder Auswertungsfehlern
     */
    EdgeNodeStats fetchAdminStats(EdgeNode node, int windowSec, Duration timeout) throws Exception;

    /**
     * Invalidiert genau eine Datei im Cache eines Edge-Knotens.
     *
     * @param node Edge-Knoten
     * @param path relativer Dateipfad
     * @return Future mit {@code true}, wenn die Invalidierung erfolgreich war
     */
    CompletableFuture<Boolean> invalidateFile(EdgeNode node, String path);

    /**
     * Invalidiert alle Cache-Einträge eines Prefixes auf einem Edge-Knoten.
     *
     * @param node Edge-Knoten
     * @param prefix Prefix für die Invalidierung
     * @return Future mit {@code true}, wenn die Invalidierung erfolgreich war
     */
    CompletableFuture<Boolean> invalidatePrefix(EdgeNode node, String prefix);

    /**
     * Leert den kompletten Cache eines Edge-Knotens.
     *
     * @param node Edge-Knoten
     * @return Future mit {@code true}, wenn die Operation erfolgreich war
     */
    CompletableFuture<Boolean> clearCache(EdgeNode node);

    /**
     * Aktualisiert die Origin-Basis-URL einer Edge zur Laufzeit über deren Admin-Config-API.
     *
     * @param node Edge-Knoten
     * @param originBaseUrl neue Origin-Basis-URL
     * @param timeout Timeout für den Request
     * @return {@code true}, wenn die Edge die Änderung akzeptiert hat
     */
    boolean updateOriginBaseUrl(EdgeNode node, String originBaseUrl, Duration timeout);

    /**
     * Prüft, ob eine Edge-Instanz über ihren Ready-Endpunkt betriebsbereit ist.
     *
     * @param baseUrl Basis-URL der Edge
     * @param timeout Timeout für den Check
     * @return {@code true}, wenn die Edge ready ist
     */
    boolean isReady(URI baseUrl, Duration timeout);
}
