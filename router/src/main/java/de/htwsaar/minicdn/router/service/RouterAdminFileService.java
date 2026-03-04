package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.OriginAdminGateway;
import de.htwsaar.minicdn.router.dto.AdminFileResult;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class RouterAdminFileService {

    private final OriginAdminGateway originAdminGateway;
    private final RoutingIndex routingIndex;
    private final EdgeGateway edgeGateway;

    public RouterAdminFileService(
            OriginAdminGateway originAdminGateway,
            RoutingIndex routingIndex,
            EdgeGateway edgeGateway) {

        this.originAdminGateway = originAdminGateway;
        this.routingIndex = routingIndex;
        this.edgeGateway = edgeGateway;
    }

    /**
     * Hochladen einer Datei zum Origin und invalidieren aller Edge-Caches in der Region (oder global).
     */
    public AdminFileResult uploadAndInvalidate(String path, byte[] body, String region) {
        try {
            // Step 1: Upload to Origin
            var uploadResult = originAdminGateway.uploadFile(path, body);

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
            var deleteResult = originAdminGateway.deleteFile(path);

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
        return originAdminGateway.listFiles(page, size);
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
                    .map(f -> f.orTimeout(5, TimeUnit.SECONDS))
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
            return edgeGateway
                    .sendDelete(node, "/api/edge/admin/cache/files/" + path)
                    .orTimeout(3, TimeUnit.SECONDS)
                    .thenApply(status -> status >= 200 && status < 300)
                    .exceptionally(ex -> false);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
