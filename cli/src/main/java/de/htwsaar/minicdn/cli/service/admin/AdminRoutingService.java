package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
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

    public CallResult addNode(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        URI url = routingUrl(routerBaseUrl, region, edgeBaseUrl);
        return send(TransportRequest.postJson(url, requestTimeout, adminJsonHeaders(), "{}"));
    }

    public CallResult removeNode(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        URI url = routingUrl(routerBaseUrl, region, edgeBaseUrl);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    public CallResult listNodes(URI routerBaseUrl, boolean checkHealth) {
        URI url = base(routerBaseUrl).resolve("api/cdn/routing?checkHealth=" + checkHealth);
        return send(TransportRequest.get(url, requestTimeout, adminHeaders()));
    }

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
