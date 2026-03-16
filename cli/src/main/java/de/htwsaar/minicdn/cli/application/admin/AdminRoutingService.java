package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für Routing-Administration.
 *
 * <p>Details der technischen Anbindung liegen vollständig im Adapter.
 * Der Service spricht nur fachliche Operationen.</p>
 */
public final class AdminRoutingService {

    private final AdminOperations adminOperations;
    private final String adminToken;

    /**
     * Erzeugt den Service.
     *
     * @param adminOperations fachlicher Port für administrative Remote-Aufrufe
     * @param adminToken Admin-Token für geschützte Operationen
     */
    public AdminRoutingService(AdminOperations adminOperations, String adminToken) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Registriert einen Edge-Knoten für eine Region im Routing.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param edgeBaseUrl Basis-URL der Edge
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult addNode(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        return adminOperations.addRoutingNode(routerBaseUrl, adminToken, region, edgeBaseUrl);
    }

    /**
     * Entfernt einen Edge-Knoten aus dem Routing.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param edgeBaseUrl Basis-URL der Edge
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult removeNode(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        return adminOperations.removeRoutingNode(routerBaseUrl, adminToken, region, edgeBaseUrl);
    }

    /**
     * Liest den aktuellen Routing-Zustand vom Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param checkHealth legt fest, ob zusätzliche Health-Prüfungen ausgeführt werden sollen
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult listNodes(URI routerBaseUrl, boolean checkHealth) {
        return adminOperations.listRoutingNodes(routerBaseUrl, adminToken, checkHealth);
    }

    /**
     * Führt ein Bulk-Update des Routing-Index aus.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param jsonBody fachlich vorbereiteter Bulk-Inhalt
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult bulkUpdate(URI routerBaseUrl, String jsonBody) {
        return adminOperations.bulkUpdateRouting(routerBaseUrl, adminToken, requireText(jsonBody, "jsonBody"));
    }

    /**
     * Validiert einen Pflichttext und liefert die getrimmte Form zurück.
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
