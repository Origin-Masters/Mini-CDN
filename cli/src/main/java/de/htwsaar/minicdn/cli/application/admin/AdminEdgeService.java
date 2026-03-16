package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für die Verwaltung von Edge-Instanzen über den Router.
 *
 * <p>Die fachliche Logik delegiert Use-Cases an den Port. Details der
 * Transportbindung bleiben vollständig im Adapter.</p>
 */
public final class AdminEdgeService {

    private final AdminOperations adminOperations;
    private final String adminToken;

    /**
     * Erzeugt den Service.
     *
     * @param adminOperations fachlicher Port für administrative Remote-Aufrufe
     * @param adminToken Admin-Token für geschützte Operationen
     */
    public AdminEdgeService(AdminOperations adminOperations, String adminToken) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Startet eine einzelne verwaltete Edge-Instanz über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion der Edge
     * @param port Netzwerk-Port der Edge
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @param autoRegister {@code true}, wenn der Router die Edge direkt registrieren soll
     * @param waitUntilReady {@code true}, wenn auf Bereitschaft gewartet werden soll
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult startEdge(
            URI routerBaseUrl,
            String region,
            int port,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {
        return adminOperations.startEdge(
                routerBaseUrl, adminToken, region, port, originBaseUrl, autoRegister, waitUntilReady);
    }

    /**
     * Stoppt eine verwaltete Edge-Instanz über ihre technische Instanz-ID.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param instanceId Instanz-ID der verwalteten Edge
     * @param deregister {@code true}, wenn die Edge aus dem Routing entfernt werden soll
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult stopEdge(URI routerBaseUrl, String instanceId, boolean deregister) {
        return adminOperations.stopEdge(routerBaseUrl, adminToken, instanceId, deregister);
    }

    /**
     * Stoppt alle verwalteten Edge-Instanzen einer Region.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param deregister {@code true}, wenn die Edges aus dem Routing entfernt werden sollen
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult stopRegion(URI routerBaseUrl, String region, boolean deregister) {
        return adminOperations.stopRegion(routerBaseUrl, adminToken, region, deregister);
    }

    /**
     * Listet alle vom Router verwalteten Edge-Instanzen auf.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult listManaged(URI routerBaseUrl) {
        return adminOperations.listManagedEdges(routerBaseUrl, adminToken);
    }

    /**
     * Startet mehrere verwaltete Edge-Instanzen mit automatischer Portvergabe.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion der Edges
     * @param count Anzahl zu startender Edges
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @param autoRegister {@code true}, wenn der Router die Edges direkt registrieren soll
     * @param waitUntilReady {@code true}, wenn auf Bereitschaft gewartet werden soll
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult startEdgesAuto(
            URI routerBaseUrl,
            String region,
            int count,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {
        return adminOperations.startEdgesAuto(
                routerBaseUrl, adminToken, region, count, originBaseUrl, autoRegister, waitUntilReady);
    }

    /**
     * Prüft, ob ein Text gesetzt ist, und liefert die getrimmte Form zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
