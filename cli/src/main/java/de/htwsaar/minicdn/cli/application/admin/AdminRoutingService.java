package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.application.support.TransportCallAdapter;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.TransportRequest;
import de.htwsaar.minicdn.cli.domain.port.TransportClient;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Routing-Admin-Aufrufe gegen den Router.
 */
public final class AdminRoutingService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final String adminToken;

    public AdminRoutingService(TransportClient transportClient, Duration requestTimeout, String adminToken) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Registriert einen Edge-Knoten für eine Region im Routing.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param edgeBaseUrl Basis-URL der Edge
     * @return normiertes Request-Ergebnis
     */
    public CallResult addNode(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        URI url = routingUrl(routerBaseUrl, region, edgeBaseUrl);
        return send(TransportRequest.postJson(url, requestTimeout, adminJsonHeaders(), "{}"));
    }

    /**
     * Entfernt einen Edge-Knoten aus dem Routing.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param edgeBaseUrl Basis-URL der Edge
     * @return normiertes Request-Ergebnis
     */
    public CallResult removeNode(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        URI url = routingUrl(routerBaseUrl, region, edgeBaseUrl);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Liest den aktuellen Routing-Zustand vom Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param checkHealth legt fest, ob der Router zusaetzlich Health-Pruefungen ausfuehren soll
     * @return normiertes Request-Ergebnis
     */
    public CallResult listNodes(URI routerBaseUrl, boolean checkHealth) {
        URI url = base(routerBaseUrl).resolve("api/cdn/routing?checkHealth=" + checkHealth);
        return send(TransportRequest.get(url, requestTimeout, adminHeaders()));
    }

    /**
     * Führt ein Bulk-Update des Routing-Index mit einem bereits vorbereiteten JSON-Body aus.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param jsonBody JSON-Payload für das Bulk-Update
     * @return normiertes Request-Ergebnis
     */
    public CallResult bulkUpdate(URI routerBaseUrl, String jsonBody) {
        String cleanBody = requireText(jsonBody, "jsonBody");
        URI url = base(routerBaseUrl).resolve("api/cdn/routing/bulk");
        return send(TransportRequest.postJson(url, requestTimeout, adminJsonHeaders(), cleanBody));
    }

    private URI routingUrl(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        String cleanRegion = UriUtils.urlEncode(requireText(region, "region"));
        String cleanEdgeUrl =
                UriUtils.urlEncode(requireText(edgeBaseUrl == null ? null : edgeBaseUrl.toString(), "url"));
        return base(routerBaseUrl).resolve("api/cdn/routing?region=" + cleanRegion + "&url=" + cleanEdgeUrl);
    }

    private CallResult send(TransportRequest request) {
        return TransportCallAdapter.execute(transportClient, request);
    }

    private Map<String, String> adminHeaders() {
        return Map.of("X-Admin-Token", adminToken);
    }

    private Map<String, String> adminJsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static URI base(URI routerBaseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(routerBaseUrl, "routerBaseUrl"));
    }

    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
