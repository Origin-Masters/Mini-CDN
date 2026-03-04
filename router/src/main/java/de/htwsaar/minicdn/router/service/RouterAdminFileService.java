package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.dto.AdminFileResult;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RouterAdminFileService {

    private final HttpClient httpClient;
    private final RoutingIndex routingIndex;
    private final String adminToken;
    private final URI originBaseUrl;
    private final Duration timeout;

    public RouterAdminFileService(
            HttpClient httpClient,
            RoutingIndex routingIndex,
            @Value("${minicdn.admin.token}") String adminToken,
            @Value("${origin.base-url:http://localhost:8080}") String originBaseUrl) {

        this.httpClient = httpClient;
        this.routingIndex = routingIndex;
        this.adminToken = adminToken;
        this.originBaseUrl = URI.create(originBaseUrl);
        this.timeout = Duration.ofSeconds(10);
    }

    /**
     * Hochladen einer Datei zum Origin und invalidieren aller Edge-Caches in der Region (oder global).
     */
    public AdminFileResult uploadAndInvalidate(String path, byte[] body, String region) {
        try {
            // Step 1: Upload to Origin
            var uploadResult = uploadToOrigin(path, body);

            if (!uploadResult.success()) {
                return uploadResult;
            }

            // Step 2: Invalidate Edge caches
            int invalidated = invalidateCaches(path, region);

            return AdminFileResult.success(
                    201, Map.of("uploaded", true, "path", path, "size", body.length, "edgesInvalidated", invalidated));

        } catch (Exception e) {
            return AdminFileResult.error(500, "Upload failed: " + e.getMessage());
        }
    }

    /**
     * Löschen einer Datei vom Origin und invalidieren aller Edge-Caches in der Region (oder global).
     */
    public AdminFileResult deleteAndInvalidate(String path, String region) {
        try {
            // Step 1: Delete from Origin
            var deleteResult = deleteFromOrigin(path);

            if (!deleteResult.success()) {
                return deleteResult;
            }

            // Step 2: Invalidate Edge caches
            int invalidated = invalidateCaches(path, region);

            return AdminFileResult.success(
                    204,
                    Map.of(
                            "deleted", true,
                            "path", path,
                            "edgesInvalidated", invalidated));

        } catch (Exception e) {
            return AdminFileResult.error(500, "Delete failed: " + e.getMessage());
        }
    }

    /**
     * Listet Dateien vom Origin auf (nur read-only, keine Cache-Invalidierung). Unterstützt Paging.
     */
    public AdminFileResult listOriginFiles(int page, int size) {
        try {
            URI listUri = originBaseUrl.resolve(String.format("/api/origin/files?page=%d&size=%d", page, size));

            HttpRequest request = HttpRequest.newBuilder(listUri)
                    .header("X-Admin-Token", adminToken)
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return AdminFileResult.success(response.statusCode(), response.body());

        } catch (Exception e) {
            return AdminFileResult.error(500, "List failed: " + e.getMessage());
        }
    }

    /**
     * Hilfsmethode zum Hochladen einer Datei zum Origin über die Admin-API.
     */
    private AdminFileResult uploadToOrigin(String path, byte[] body) throws Exception {
        URI uploadUri = originBaseUrl.resolve("/api/origin/admin/files/" + path);

        HttpRequest request = HttpRequest.newBuilder(uploadUri)
                .header("X-Admin-Token", adminToken)
                .header("Content-Type", "application/octet-stream")
                .timeout(timeout)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return AdminFileResult.success(response.statusCode(), null);
        }

        return AdminFileResult.error(response.statusCode(), "Origin upload failed: " + response.body());
    }

    private AdminFileResult deleteFromOrigin(String path) throws Exception {
        URI deleteUri = originBaseUrl.resolve("/api/origin/admin/files/" + path);

        HttpRequest request = HttpRequest.newBuilder(deleteUri)
                .header("X-Admin-Token", adminToken)
                .timeout(timeout)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return AdminFileResult.success(response.statusCode(), null);
        }

        return AdminFileResult.error(response.statusCode(), "Origin delete failed: " + response.body());
    }

    /**
     * Invalidiert die Caches aller Edge-Knoten in der angegebenen Region (oder global, wenn region null/blank).
     */
    private int invalidateCaches(String path, String region) {
        List<String> regionsToInvalidate =
                region != null && !region.isBlank() ? List.of(region.trim()) : routingIndex.getAllRegions();

        int totalInvalidated = 0;

        for (String reg : regionsToInvalidate) {
            List<EdgeNode> nodes = routingIndex.getAllNodes(reg);

            List<CompletableFuture<Boolean>> futures =
                    nodes.stream().map(node -> invalidateEdgeCache(node, path)).toList();

            // Wait for all invalidations (with timeout)
            long successCount = futures.stream()
                    .map(f -> f.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS))
                    .map(f -> f.exceptionally(ex -> false).join())
                    .filter(success -> success)
                    .count();

            totalInvalidated += successCount;
        }

        return totalInvalidated;
    }

    /**
     * Sendet eine Cache-Invalidierungsanfrage an einen Edge-Knoten für den angegebenen Pfad.
     */
    private CompletableFuture<Boolean> invalidateEdgeCache(EdgeNode node, String path) {
        try {
            URI invalidateUri = URI.create(node.url() + "/api/edge/admin/cache/files/" + path);

            HttpRequest request = HttpRequest.newBuilder(invalidateUri)
                    .header("X-Admin-Token", adminToken)
                    .timeout(Duration.ofSeconds(3))
                    .DELETE()
                    .build();

            return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300)
                    .exceptionally(ex -> false);

        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
