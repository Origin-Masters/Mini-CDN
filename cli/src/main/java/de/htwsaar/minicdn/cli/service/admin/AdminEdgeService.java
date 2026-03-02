package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für die Admin-API des Routers zum Starten/Stoppen und Auflisten
 * von "managed" Edge-Instanzen.
 *
 * <p>Die Klasse kennt keine konkrete Transportschicht mehr.
 */
public final class AdminEdgeService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    public AdminEdgeService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public HttpCallResult startEdge(
            URI routerBaseUrl,
            String region,
            int port,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/start");

        String json = "{"
                + "\"region\":\"" + JsonUtils.escapeJson(region.trim()) + "\","
                + "\"port\":" + port + ","
                + "\"originBaseUrl\":\"" + JsonUtils.escapeJson(originBaseUrl.toString()) + "\","
                + "\"autoRegister\":" + autoRegister + ","
                + "\"waitUntilReady\":" + waitUntilReady
                + "}";

        TransportResponse response = transportClient.send(TransportRequest.postJson(
                url,
                requestTimeout,
                Map.of("X-Admin-Token", resolveAdminToken(), "Content-Type", "application/json"),
                json));

        return toHttpCallResult(response);
    }

    public HttpCallResult stopEdge(URI routerBaseUrl, String instanceId, boolean deregister) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(instanceId, "instanceId");

        String trimmed = instanceId.trim();
        if (!isSafeInstanceId(trimmed)) {
            return HttpCallResult.clientError("Invalid instanceId (expected pattern: [A-Za-z0-9_-]+).");
        }

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/" + trimmed + "?deregister=" + deregister);

        TransportResponse response = transportClient.send(
                TransportRequest.delete(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));

        return toHttpCallResult(response);
    }

    public HttpCallResult stopRegion(URI routerBaseUrl, String region, boolean deregister) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(region, "region");

        String trimmed = region.trim();
        if (trimmed.isBlank()) {
            return HttpCallResult.clientError("Invalid region (must not be blank).");
        }

        String encodedRegion = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/region/" + encodedRegion + "?deregister=" + deregister);

        TransportResponse response = transportClient.send(
                TransportRequest.delete(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));

        return toHttpCallResult(response);
    }

    public HttpCallResult listManaged(URI routerBaseUrl) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/managed");

        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));

        return toHttpCallResult(response);
    }

    public HttpCallResult startEdgesAuto(
            URI routerBaseUrl,
            String region,
            int count,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/start/auto");

        String json = String.format(
                "{\"region\":\"%s\",\"count\":%d,\"originBaseUrl\":\"%s\",\"autoRegister\":%s,\"waitUntilReady\":%s}",
                JsonUtils.escapeJson(region.trim()),
                count,
                JsonUtils.escapeJson(originBaseUrl.toString()),
                autoRegister,
                waitUntilReady);

        TransportResponse response = transportClient.send(TransportRequest.postJson(
                url,
                requestTimeout,
                Map.of("X-Admin-Token", resolveAdminToken(), "Content-Type", "application/json"),
                json));

        return toHttpCallResult(response);
    }

    private static HttpCallResult toHttpCallResult(TransportResponse response) {
        if (response.error() != null) {
            return HttpCallResult.ioError(response.error());
        }
        return HttpCallResult.http(Objects.requireNonNull(response.statusCode(), "statusCode"), response.body());
    }

    private static String resolveAdminToken() {
        String token = System.getenv("MINICDN_ADMIN_TOKEN");
        if (token == null || token.isBlank()) {
            token = System.getProperty("minicdn.admin.token");
        }
        if (token == null || token.isBlank()) {
            token = "secret-token";
        }
        return token;
    }

    private static boolean isSafeInstanceId(String s) {
        return !s.isBlank() && s.matches("[A-Za-z0-9_-]+");
    }
}
