package de.htwsaar.minicdn.cli.adapter.out.http;

import de.htwsaar.minicdn.cli.adapter.in.cli.support.JsonUtils;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.ManagedEdgeStartResult;
import de.htwsaar.minicdn.cli.domain.port.SystemBootstrapGateway;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-Adapter für bootstrap-relevante Remote-Operationen.
 */
public final class HttpSystemBootstrapGateway implements SystemBootstrapGateway {

    private final TransportClient transportClient;

    /**
     * Erzeugt den Adapter.
     *
     * @param transportClient HTTP-basierter Transportadapter
     */
    public HttpSystemBootstrapGateway(TransportClient transportClient) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRouterHealthy(URI routerBaseUrl, Duration timeout, String adminToken) {
        URI uri = base(routerBaseUrl).resolve("api/cdn/health");
        Map<String, String> headers =
                adminToken == null || adminToken.isBlank() ? Map.of() : Map.of("X-Admin-Token", adminToken);
        TransportResponse response = transportClient.send(TransportRequest.get(uri, timeout, headers));
        return response.error() == null && response.is2xx();
    }

    /** {@inheritDoc} */
    @Override
    public ManagedEdgeStartResult startManagedEdge(
            URI routerBaseUrl, Duration timeout, String adminToken, String region, int port, String originBaseUrl) {

        URI uri = base(routerBaseUrl).resolve("api/cdn/admin/edges/start");
        String jsonBody = "{"
                + "\"region\":\"" + JsonUtils.escapeJson(region) + "\","
                + "\"port\":" + port + ","
                + "\"originBaseUrl\":\"" + JsonUtils.escapeJson(originBaseUrl) + "\","
                + "\"autoRegister\":true,"
                + "\"waitUntilReady\":true"
                + "}";

        Map<String, String> headers = adminToken == null || adminToken.isBlank()
                ? HttpAdapterSupport.jsonHeaders()
                : HttpAdapterSupport.adminJsonHeaders(adminToken);

        TransportResponse response = transportClient.send(TransportRequest.postJson(uri, timeout, headers, jsonBody));
        if (response.error() != null) {
            return ManagedEdgeStartResult.failed(response.error());
        }
        Integer statusCode = response.statusCode();
        if (statusCode != null && statusCode >= 200 && statusCode < 300) {
            return ManagedEdgeStartResult.started();
        }
        if (Integer.valueOf(409).equals(statusCode)) {
            return ManagedEdgeStartResult.conflict(response.body());
        }
        return ManagedEdgeStartResult.failed(response.body());
    }

    private static URI base(URI routerBaseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(routerBaseUrl, "routerBaseUrl"));
    }
}
