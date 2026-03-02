package de.htwsaar.minicdn.router.domain;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Port für die Kommunikation mit Edge-Knoten.
 *
 * <p>Die Fachlogik hängt nur von diesem Interface ab und kennt keine konkreten
 * HTTP- oder gRPC-Implementierungen.</p>
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
     * Sendet asynchron ein DELETE an einen Edge-Endpunkt.
     *
     * @param node Edge-Knoten
     * @param endpoint relativer Endpunkt
     * @return Future mit dem HTTP-Statuscode
     */
    CompletableFuture<Integer> sendDelete(EdgeNode node, String endpoint);

    /**
     * Prüft, ob eine Edge-Instanz über ihren Ready-Endpunkt betriebsbereit ist.
     *
     * @param baseUrl Basis-URL der Edge
     * @param timeout Timeout für den Check
     * @return {@code true}, wenn die Edge ready ist
     */
    boolean isReady(URI baseUrl, Duration timeout);
}
