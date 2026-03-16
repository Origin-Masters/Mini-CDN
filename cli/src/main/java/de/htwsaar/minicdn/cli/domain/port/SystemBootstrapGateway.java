package de.htwsaar.minicdn.cli.domain.port;

import de.htwsaar.minicdn.cli.domain.model.ManagedEdgeStartResult;
import java.net.URI;
import java.time.Duration;

/**
 * Fachlicher Port für bootstrap-relevante Remote-Operationen der CLI.
 */
public interface SystemBootstrapGateway {

    /**
     * Prüft, ob der Router betriebsbereit ist.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout für den Remote-Aufruf
     * @param adminToken optionales Admin-Token
     * @return {@code true}, wenn der Router erreichbar und gesund ist
     */
    boolean isRouterHealthy(URI routerBaseUrl, Duration timeout, String adminToken);

    /**
     * Startet eine verwaltete Edge-Instanz über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout für den Remote-Aufruf
     * @param adminToken optionales Admin-Token
     * @param region Zielregion der Edge
     * @param port Netzwerk-Port der Edge
     * @param originBaseUrl Basis-URL des Origins
     * @return normiertes Ergebnis des Startvorgangs
     */
    ManagedEdgeStartResult startManagedEdge(
            URI routerBaseUrl, Duration timeout, String adminToken, String region, int port, String originBaseUrl);
}
