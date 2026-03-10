package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Remote-Konfiguration von Origin- und Edge-Servern.
 */
public final class AdminConfigService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    public AdminConfigService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public HttpCallResult getOriginConfig(URI originBaseUrl) {
        return sendGet(originBaseUrl, "api/origin/admin/config");
    }

    public HttpCallResult patchOriginConfig(URI originBaseUrl, Long maxUploadBytes, String logLevel) {
        StringBuilder json = new StringBuilder("{");
        boolean hasField = false;
        if (maxUploadBytes != null) {
            json.append("\"maxUploadBytes\":").append(maxUploadBytes);
            hasField = true;
        }
        if (logLevel != null && !logLevel.isBlank()) {
            if (hasField) {
                json.append(',');
            }
            json.append("\"logLevel\":\"")
                    .append(JsonUtils.escapeJson(logLevel.trim()))
                    .append("\"");
            hasField = true;
        }
        if (!hasField) {
            return HttpCallResult.clientError("at least one field must be provided");
        }
        json.append('}');
        return sendPatch(originBaseUrl, "api/origin/admin/config", json.toString());
    }

    public HttpCallResult getEdgeConfig(URI edgeBaseUrl) {
        return sendGet(edgeBaseUrl, "api/edge/admin/config");
    }

    public HttpCallResult patchEdgeConfig(
            URI edgeBaseUrl, String region, Long defaultTtlMs, Integer maxEntries, String replacementStrategy) {
        StringBuilder json = new StringBuilder("{");
        boolean hasField = false;
        if (region != null && !region.isBlank()) {
            json.append("\"region\":\"")
                    .append(JsonUtils.escapeJson(region.trim()))
                    .append("\"");
            hasField = true;
        }
        if (defaultTtlMs != null) {
            if (hasField) {
                json.append(',');
            }
            json.append("\"defaultTtlMs\":").append(defaultTtlMs);
            hasField = true;
        }
        if (maxEntries != null) {
            if (hasField) {
                json.append(',');
            }
            json.append("\"maxEntries\":").append(maxEntries);
            hasField = true;
        }
        if (replacementStrategy != null && !replacementStrategy.isBlank()) {
            if (hasField) {
                json.append(',');
            }
            json.append("\"replacementStrategy\":\"")
                    .append(JsonUtils.escapeJson(replacementStrategy.trim().toUpperCase()))
                    .append("\"");
            hasField = true;
        }
        if (!hasField) {
            return HttpCallResult.clientError("at least one field must be provided");
        }
        json.append('}');
        return sendPatch(edgeBaseUrl, "api/edge/admin/config", json.toString());
    }

    public HttpCallResult getEdgeTtlPolicies(URI edgeBaseUrl) {
        return sendGet(edgeBaseUrl, "api/edge/admin/config/ttl");
    }

    public HttpCallResult setEdgeTtlPolicy(URI edgeBaseUrl, String prefix, Long ttlMs) {
        if (prefix == null || prefix.isBlank()) {
            return HttpCallResult.clientError("prefix must not be blank");
        }
        if (ttlMs == null) {
            return HttpCallResult.clientError("ttlMs must not be null");
        }

        String json = "{\"prefix\":\"" + JsonUtils.escapeJson(prefix.trim()) + "\",\"ttlMs\":" + ttlMs + '}';
        return sendPut(edgeBaseUrl, "api/edge/admin/config/ttl", json);
    }

    public HttpCallResult removeEdgeTtlPolicy(URI edgeBaseUrl, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return HttpCallResult.clientError("prefix must not be blank");
        }
        URI url = UriUtils.ensureTrailingSlash(edgeBaseUrl)
                .resolve("api/edge/admin/config/ttl?prefix=" + UriUtils.urlEncode(prefix.trim()));
        TransportResponse response = transportClient.send(
                TransportRequest.delete(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));
        return toResult(response);
    }

    private HttpCallResult sendGet(URI baseUrl, String path) {
        URI url = UriUtils.ensureTrailingSlash(baseUrl).resolve(path);
        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));
        return toResult(response);
    }

    private HttpCallResult sendPatch(URI baseUrl, String path, String jsonBody) {
        URI url = UriUtils.ensureTrailingSlash(baseUrl).resolve(path);
        TransportResponse response = transportClient.send(TransportRequest.patchJson(
                url,
                requestTimeout,
                Map.of("X-Admin-Token", resolveAdminToken(), "Content-Type", "application/json"),
                jsonBody));
        return toResult(response);
    }

    private HttpCallResult sendPut(URI baseUrl, String path, String jsonBody) {
        URI url = UriUtils.ensureTrailingSlash(baseUrl).resolve(path);
        TransportResponse response = transportClient.send(TransportRequest.putJson(
                url,
                requestTimeout,
                Map.of("X-Admin-Token", resolveAdminToken(), "Content-Type", "application/json"),
                jsonBody));
        return toResult(response);
    }

    private static HttpCallResult toResult(TransportResponse response) {
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
}
