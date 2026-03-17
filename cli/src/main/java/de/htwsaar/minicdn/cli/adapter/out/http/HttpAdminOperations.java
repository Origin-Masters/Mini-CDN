package de.htwsaar.minicdn.cli.adapter.out.http;

import com.fasterxml.jackson.databind.JsonNode;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.StatsResponse;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import de.htwsaar.minicdn.common.serialization.JacksonCodec;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-Adapter für administrative CLI-Operationen.
 */
public final class HttpAdminOperations implements AdminOperations {

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    /**
     * Erzeugt den Adapter.
     *
     * @param transportClient HTTP-basierter Transportadapter
     * @param requestTimeout Standard-Timeout für Remote-Aufrufe
     */
    public HttpAdminOperations(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /** {@inheritDoc} */
    @Override
    public CallResult ping(URI baseUrl, String relativePath) {
        String cleanPath =
                PathUtils.stripLeadingSlash(Objects.toString(relativePath, "").trim());
        if (cleanPath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        URI url = base(baseUrl).resolve(cleanPath);
        return send(TransportRequest.get(url, requestTimeout, Map.of()));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult invalidateFile(URI routerBaseUrl, String adminToken, String region, String path) {
        String cleanRegion = UriUtils.urlEncode(HttpAdapterSupport.requireText(region, "region"));
        String cleanPath = PathUtils.normalizeRelativePath(path);
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/cache/region/" + cleanRegion + "/files/" + cleanPath);
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult invalidatePrefix(URI routerBaseUrl, String adminToken, String region, String prefix) {
        String cleanRegion = UriUtils.urlEncode(HttpAdapterSupport.requireText(region, "region"));
        String cleanPrefix = UriUtils.urlEncode(PathUtils.normalizeRelativePath(prefix));
        URI url = base(routerBaseUrl)
                .resolve("api/cdn/admin/cache/region/" + cleanRegion + "/prefix?value=" + cleanPrefix);
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult clearRegion(URI routerBaseUrl, String adminToken, String region) {
        String cleanRegion = UriUtils.urlEncode(HttpAdapterSupport.requireText(region, "region"));
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/cache/region/" + cleanRegion + "/all");
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult getOriginConfig(URI originBaseUrl, String adminToken) {
        return sendGet(originBaseUrl, adminToken, "api/origin/admin/config");
    }

    /** {@inheritDoc} */
    @Override
    public CallResult patchOriginConfig(URI originBaseUrl, String adminToken, Long maxUploadBytes, String logLevel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (maxUploadBytes != null) {
            payload.put("maxUploadBytes", maxUploadBytes);
        }
        if (hasText(logLevel)) {
            payload.put("logLevel", logLevel.trim());
        }
        return sendPatch(originBaseUrl, adminToken, "api/origin/admin/config", JacksonCodec.toJson(payload));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult getOriginCluster(URI routerBaseUrl, String adminToken, boolean checkHealth) {
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/origin/cluster?checkHealth=" + checkHealth);
        return send(TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult addOriginSpare(URI routerBaseUrl, String adminToken, URI spareBaseUrl) {
        String url = UriUtils.urlEncode(
                HttpAdapterSupport.requireText(spareBaseUrl == null ? null : spareBaseUrl.toString(), "url"));
        URI target = base(routerBaseUrl).resolve("api/cdn/admin/origin/spares?url=" + url);
        return send(TransportRequest.postJson(
                target, requestTimeout, HttpAdapterSupport.adminJsonHeaders(adminToken), "{}"));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult removeOriginSpare(URI routerBaseUrl, String adminToken, URI spareBaseUrl) {
        String url = UriUtils.urlEncode(
                HttpAdapterSupport.requireText(spareBaseUrl == null ? null : spareBaseUrl.toString(), "url"));
        URI target = base(routerBaseUrl).resolve("api/cdn/admin/origin/spares?url=" + url);
        return send(TransportRequest.delete(target, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult promoteOriginSpare(URI routerBaseUrl, String adminToken, URI spareBaseUrl) {
        String url = UriUtils.urlEncode(
                HttpAdapterSupport.requireText(spareBaseUrl == null ? null : spareBaseUrl.toString(), "url"));
        URI target = base(routerBaseUrl).resolve("api/cdn/admin/origin/promote?url=" + url);
        return send(TransportRequest.postJson(
                target, requestTimeout, HttpAdapterSupport.adminJsonHeaders(adminToken), "{}"));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult checkOriginFailover(URI routerBaseUrl, String adminToken) {
        URI target = base(routerBaseUrl).resolve("api/cdn/admin/origin/failover/check");
        return send(TransportRequest.postJson(
                target, requestTimeout, HttpAdapterSupport.adminJsonHeaders(adminToken), "{}"));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult getEdgeConfig(URI edgeBaseUrl, String adminToken) {
        return sendGet(edgeBaseUrl, adminToken, "api/edge/admin/configs");
    }

    /** {@inheritDoc} */
    @Override
    public CallResult patchEdgeConfig(
            URI edgeBaseUrl,
            String adminToken,
            String region,
            Long defaultTtlMs,
            Integer maxEntries,
            String replacementStrategy,
            URI originBaseUrl) {

        Map<String, Object> payload = new LinkedHashMap<>();
        if (hasText(region)) {
            payload.put("region", region.trim());
        }
        if (defaultTtlMs != null) {
            payload.put("defaultTtlMs", defaultTtlMs);
        }
        if (maxEntries != null) {
            payload.put("maxEntries", maxEntries);
        }
        if (hasText(replacementStrategy)) {
            payload.put("replacementStrategy", replacementStrategy.trim().toUpperCase());
        }
        if (originBaseUrl != null) {
            payload.put("originBaseUrl", originBaseUrl.toString());
        }
        return sendPatch(edgeBaseUrl, adminToken, "api/edge/admin/configs", JacksonCodec.toJson(payload));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult getEdgeTtlPolicies(URI edgeBaseUrl, String adminToken) {
        return sendGet(edgeBaseUrl, adminToken, "api/edge/admin/configs/expirations");
    }

    /** {@inheritDoc} */
    @Override
    public CallResult setEdgeTtlPolicy(URI edgeBaseUrl, String adminToken, String prefix, Long ttlMs) {
        String cleanPrefix = HttpAdapterSupport.requireText(prefix, "prefix");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prefix", cleanPrefix);
        payload.put("ttlMs", ttlMs);
        return sendPut(edgeBaseUrl, adminToken, "api/edge/admin/configs/expirations", JacksonCodec.toJson(payload));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult removeEdgeTtlPolicy(URI edgeBaseUrl, String adminToken, String prefix) {
        String cleanPrefix = HttpAdapterSupport.requireText(prefix, "prefix");
        URI url = base(edgeBaseUrl)
                .resolve("api/edge/admin/configs/expirations?prefix=" + UriUtils.urlEncode(cleanPrefix));
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult startEdge(
            URI routerBaseUrl,
            String adminToken,
            String region,
            int port,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("region", HttpAdapterSupport.requireText(region, "region"));
        payload.put("port", port);
        payload.put(
                "originBaseUrl",
                Objects.requireNonNull(originBaseUrl, "originBaseUrl").toString());
        payload.put("autoRegister", autoRegister);
        payload.put("waitUntilReady", waitUntilReady);

        URI url = base(routerBaseUrl).resolve("api/cdn/admin/edges/start");
        return send(TransportRequest.postJson(
                url,
                managedEdgeStartTimeout(1, waitUntilReady),
                HttpAdapterSupport.adminJsonHeaders(adminToken),
                JacksonCodec.toJson(payload)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult stopEdge(URI routerBaseUrl, String adminToken, String instanceId, boolean deregister) {
        String cleanInstanceId = normalizeInstanceId(instanceId);
        String path = "api/cdn/admin/edges/" + cleanInstanceId + "?deregister=" + deregister;
        URI url = base(routerBaseUrl).resolve(path);
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult stopRegion(URI routerBaseUrl, String adminToken, String region, boolean deregister) {
        String cleanRegion = HttpAdapterSupport.requireText(region, "region");
        String encodedRegion = UriUtils.urlEncode(cleanRegion);
        String path = "api/cdn/admin/edges/region/" + encodedRegion + "?deregister=" + deregister;
        URI url = base(routerBaseUrl).resolve(path);
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult listManagedEdges(URI routerBaseUrl, String adminToken) {
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/edges/managed");
        return send(TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult startEdgesAuto(
            URI routerBaseUrl,
            String adminToken,
            String region,
            int count,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("region", HttpAdapterSupport.requireText(region, "region"));
        payload.put("count", count);
        payload.put(
                "originBaseUrl",
                Objects.requireNonNull(originBaseUrl, "originBaseUrl").toString());
        payload.put("autoRegister", autoRegister);
        payload.put("waitUntilReady", waitUntilReady);

        URI url = base(routerBaseUrl).resolve("api/cdn/admin/edges/start/auto");
        return send(TransportRequest.postJson(
                url,
                managedEdgeStartTimeout(count, waitUntilReady),
                HttpAdapterSupport.adminJsonHeaders(adminToken),
                JacksonCodec.toJson(payload)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult uploadFile(
            URI routerBaseUrl,
            String adminToken,
            long loggedInUserId,
            String targetPath,
            Path localFile,
            String region) {
        String cleanPath = normalizeTargetPath(targetPath, "targetPath");
        Path fileToUpload = Objects.requireNonNull(localFile, "localFile");
        String cleanRegion = HttpAdapterSupport.requireText(region, "region");
        String pathAndQuery = "api/cdn/admin/files/" + cleanPath + "?region=" + UriUtils.urlEncode(cleanRegion);
        URI url = base(routerBaseUrl).resolve(pathAndQuery);
        Map<String, String> headers = new LinkedHashMap<>(HttpAdapterSupport.adminHeaders(adminToken, loggedInUserId));
        headers.put("Content-Type", "application/octet-stream");
        return send(TransportRequest.putFile(url, requestTimeout, headers, fileToUpload));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult deleteFile(
            URI routerBaseUrl, String adminToken, long loggedInUserId, String targetPath, String region) {
        String cleanPath = normalizeTargetPath(targetPath, "path");
        String cleanRegion = HttpAdapterSupport.requireText(region, "region");
        String pathAndQuery = "api/cdn/admin/files/" + cleanPath + "?region=" + UriUtils.urlEncode(cleanRegion);
        URI url = base(routerBaseUrl).resolve(pathAndQuery);
        return send(TransportRequest.delete(
                url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken, loggedInUserId)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult listFiles(URI routerBaseUrl, String adminToken, long loggedInUserId) {
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/files");
        return send(
                TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken, loggedInUserId)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult showFile(URI routerBaseUrl, String adminToken, long loggedInUserId, String targetPath) {
        String cleanPath = normalizeTargetPath(targetPath, "path");
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/files/" + cleanPath);
        return send(
                TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken, loggedInUserId)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult addRoutingNode(URI routerBaseUrl, String adminToken, String region, URI edgeBaseUrl) {
        URI url = routingUrl(routerBaseUrl, region, edgeBaseUrl);
        return send(
                TransportRequest.postJson(url, requestTimeout, HttpAdapterSupport.adminJsonHeaders(adminToken), "{}"));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult removeRoutingNode(URI routerBaseUrl, String adminToken, String region, URI edgeBaseUrl) {
        URI url = routingUrl(routerBaseUrl, region, edgeBaseUrl);
        return send(TransportRequest.delete(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult listRoutingNodes(URI routerBaseUrl, String adminToken, boolean checkHealth) {
        URI url = base(routerBaseUrl).resolve("api/cdn/routing?checkHealth=" + checkHealth);
        return send(TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult bulkUpdateRouting(URI routerBaseUrl, String adminToken, String jsonBody) {
        URI url = base(routerBaseUrl).resolve("api/cdn/routing/bulk");
        return send(TransportRequest.postJson(
                url,
                requestTimeout,
                HttpAdapterSupport.adminJsonHeaders(adminToken),
                HttpAdapterSupport.requireText(jsonBody, "jsonBody")));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult addUser(URI routerBaseUrl, String adminToken, long loggedInUserId, String name, int role) {
        Map<String, Object> payload = Map.of("name", HttpAdapterSupport.requireText(name, "name"), "role", role);
        return send(TransportRequest.postJson(
                usersUrl(routerBaseUrl),
                requestTimeout,
                HttpAdapterSupport.adminJsonHeaders(adminToken, loggedInUserId),
                JacksonCodec.toJson(payload)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult listUsers(URI routerBaseUrl, String adminToken, long loggedInUserId) {
        return send(TransportRequest.get(
                usersUrl(routerBaseUrl), requestTimeout, HttpAdapterSupport.adminHeaders(adminToken, loggedInUserId)));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult deleteUser(URI routerBaseUrl, String adminToken, long loggedInUserId, long id) {
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/users/" + id);
        return send(TransportRequest.delete(
                url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken, loggedInUserId)));
    }

    /** {@inheritDoc} */
    @Override
    public StatsResponse fetchStats(URI routerBaseUrl, int windowSec, boolean aggregateEdge, String adminToken) {
        URI url = base(routerBaseUrl)
                .resolve("api/cdn/admin/stats?windowSec=" + windowSec + "&aggregateEdge=" + aggregateEdge);
        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));

        if (response.error() != null) {
            return StatsResponse.transportError(response.error());
        }

        int statusCode = Objects.requireNonNull(response.statusCode(), "statusCode");
        String rawBody = Objects.toString(response.body(), "");
        if (!response.is2xx()) {
            return statusCode >= 400 && statusCode < 500
                    ? StatsResponse.rejected(statusCode, rawBody)
                    : StatsResponse.serverError(statusCode, rawBody);
        }

        try {
            JsonNode jsonData = JacksonCodec.fromJson(rawBody, JsonNode.class);
            return StatsResponse.success(statusCode, rawBody, jsonData);
        } catch (RuntimeException ex) {
            return StatsResponse.parsingError(statusCode, rawBody, ex.getMessage());
        }
    }

    private CallResult sendGet(URI baseUrl, String adminToken, String path) {
        URI url = base(baseUrl).resolve(path);
        return send(TransportRequest.get(url, requestTimeout, HttpAdapterSupport.adminHeaders(adminToken)));
    }

    private CallResult sendPatch(URI baseUrl, String adminToken, String path, String jsonBody) {
        URI url = base(baseUrl).resolve(path);
        return send(TransportRequest.patchJson(
                url, requestTimeout, HttpAdapterSupport.adminJsonHeaders(adminToken), jsonBody));
    }

    private CallResult sendPut(URI baseUrl, String adminToken, String path, String jsonBody) {
        URI url = base(baseUrl).resolve(path);
        return send(TransportRequest.putJson(
                url, requestTimeout, HttpAdapterSupport.adminJsonHeaders(adminToken), jsonBody));
    }

    private CallResult send(TransportRequest request) {
        return HttpAdapterSupport.execute(transportClient, request);
    }

    private static URI base(URI baseUrl) {
        return HttpAdapterSupport.base(baseUrl);
    }

    private static URI usersUrl(URI routerBaseUrl) {
        return base(routerBaseUrl).resolve("api/cdn/admin/users");
    }

    private static String normalizeTargetPath(String rawPath, String fieldName) {
        String cleanPath =
                PathUtils.stripLeadingSlash(Objects.toString(rawPath, "").trim());
        if (cleanPath.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return cleanPath;
    }

    private static URI routingUrl(URI routerBaseUrl, String region, URI edgeBaseUrl) {
        String cleanRegion = UriUtils.urlEncode(HttpAdapterSupport.requireText(region, "region"));
        String cleanEdgeUrl = UriUtils.urlEncode(
                HttpAdapterSupport.requireText(edgeBaseUrl == null ? null : edgeBaseUrl.toString(), "url"));
        return base(routerBaseUrl).resolve("api/cdn/routing?region=" + cleanRegion + "&url=" + cleanEdgeUrl);
    }

    private static String normalizeInstanceId(String instanceId) {
        String trimmed = HttpAdapterSupport.requireText(instanceId, "instanceId");
        if (!trimmed.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("instanceId must match [A-Za-z0-9_-]+");
        }
        return trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Duration managedEdgeStartTimeout(int edgeCount, boolean waitUntilReady) {
        if (!waitUntilReady) {
            return requestTimeout;
        }
        Duration readyWaitBudgetPerEdge = Duration.ofSeconds(8);
        Duration readyWaitRequestBuffer = Duration.ofSeconds(5);
        long totalReadyWaitMillis = readyWaitBudgetPerEdge.toMillis() * Math.max(1, edgeCount);
        Duration requiredTimeout = Duration.ofMillis(totalReadyWaitMillis).plus(readyWaitRequestBuffer);
        return requestTimeout.compareTo(requiredTimeout) >= 0 ? requestTimeout : requiredTimeout;
    }
}
