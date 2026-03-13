package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.OriginAdminGateway;
import de.htwsaar.minicdn.router.dto.AdminFileResult;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Fachlicher Service für Admin-Dateioperationen über den Router.
 *
 * <p>Schreiboperationen werden an den Origin delegiert.
 * Die anschließende Cache-Invalidierung nutzt nur fachliche Edge-Operationen.</p>
 */
@Service
public class RouterAdminFileService {

    private final OriginAdminGateway originAdminGateway;
    private final OriginClusterService originClusterService;
    private final RoutingIndex routingIndex;
    private final EdgeGateway edgeGateway;

    public RouterAdminFileService(
            OriginAdminGateway originAdminGateway,
            OriginClusterService originClusterService,
            RoutingIndex routingIndex,
            EdgeGateway edgeGateway) {

        this.originAdminGateway = originAdminGateway;
        this.originClusterService = originClusterService;
        this.routingIndex = routingIndex;
        this.edgeGateway = edgeGateway;
    }

    /**
     * Lädt eine Datei zum Origin hoch und invalidiert betroffene Edge-Caches.
     *
     * @param path relativer Dateipfad
     * @param body Dateiinhalt
     * @param region optionale Zielregion; leer bedeutet alle Regionen
     * @return Ergebnis der Gesamtoperation
     */
    public AdminFileResult uploadAndInvalidate(String path, byte[] body, String region) {
        try {
            String activeOrigin = originClusterService.resolveActiveOrigin();
            if (activeOrigin == null || activeOrigin.isBlank()) {
                return AdminFileResult.error(503, "No active origin available");
            }

            AdminFileResult uploadResult = originAdminGateway.uploadFile(activeOrigin, path, body);
            if (!uploadResult.success()) {
                return uploadResult;
            }

            ReplicationResult replication = replicateUpload(path, body);

            int invalidated = invalidateCaches(path, region);

            return AdminFileResult.success(
                    201,
                    Map.of(
                            "uploaded",
                            true,
                            "path",
                            path,
                            "size",
                            body.length,
                            "activeOrigin",
                            activeOrigin,
                            "spareReplication",
                            replication.toMap(),
                            "edgesInvalidated",
                            invalidated));
        } catch (Exception e) {
            return AdminFileResult.error(500, "Upload failed: " + e.getMessage());
        }
    }

    /**
     * Löscht eine Datei im Origin und invalidiert betroffene Edge-Caches.
     *
     * @param path relativer Dateipfad
     * @param region optionale Zielregion; leer bedeutet alle Regionen
     * @return Ergebnis der Gesamtoperation
     */
    public AdminFileResult deleteAndInvalidate(String path, String region) {
        try {
            String activeOrigin = originClusterService.resolveActiveOrigin();
            if (activeOrigin == null || activeOrigin.isBlank()) {
                return AdminFileResult.error(503, "No active origin available");
            }

            AdminFileResult deleteResult = originAdminGateway.deleteFile(activeOrigin, path);
            if (!deleteResult.success()) {
                return deleteResult;
            }

            ReplicationResult replication = replicateDelete(path);

            int invalidated = invalidateCaches(path, region);

            return AdminFileResult.success(
                    204,
                    Map.of(
                            "deleted", true,
                            "path", path,
                            "activeOrigin", activeOrigin,
                            "spareReplication", replication.toMap(),
                            "edgesInvalidated", invalidated));
        } catch (Exception e) {
            return AdminFileResult.error(500, "Delete failed: " + e.getMessage());
        }
    }

    /**
     * Listet Dateien aus dem Origin auf.
     *
     * @param page Seitennummer
     * @param size Seitengröße
     * @return Ergebnis der Origin-Abfrage
     */
    public AdminFileResult listOriginFiles(int page, int size) {
        String activeOrigin = originClusterService.resolveActiveOrigin();
        if (activeOrigin == null || activeOrigin.isBlank()) {
            return AdminFileResult.error(503, "No active origin available");
        }
        return originAdminGateway.listFiles(activeOrigin, page, size);
    }

    /**
     * Invalidiert die Caches aller Edge-Knoten in einer Region oder global.
     *
     * @param path relativer Dateipfad
     * @param region optionale Zielregion
     * @return Anzahl erfolgreicher Invalidierungen
     */
    private int invalidateCaches(String path, String region) {
        List<String> regionsToInvalidate =
                region != null && !region.isBlank() ? List.of(region.trim()) : routingIndex.getAllRegions();

        int totalInvalidated = 0;

        for (String reg : regionsToInvalidate) {
            List<EdgeNode> nodes = routingIndex.getAllNodes(reg);

            List<CompletableFuture<Boolean>> futures =
                    nodes.stream().map(node -> invalidateEdgeCache(node, path)).toList();

            long successCount = futures.stream()
                    .map(future -> future.orTimeout(5, TimeUnit.SECONDS))
                    .map(future -> future.exceptionally(ex -> false).join())
                    .filter(Boolean.TRUE::equals)
                    .count();

            totalInvalidated += (int) successCount;
        }

        return totalInvalidated;
    }

    /**
     * Führt eine fachliche Datei-Invalidierung auf einem Edge-Knoten aus.
     *
     * @param node Edge-Knoten
     * @param path relativer Dateipfad
     * @return Future mit {@code true}, wenn die Invalidierung erfolgreich war
     */
    private CompletableFuture<Boolean> invalidateEdgeCache(EdgeNode node, String path) {
        try {
            return edgeGateway
                    .invalidateFile(node, path)
                    .orTimeout(3, TimeUnit.SECONDS)
                    .exceptionally(ex -> false);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Liefert Metadaten zu einer Datei im Origin.
     */
    public AdminFileResult showOriginFile(String path) {
        String activeOrigin = originClusterService.resolveActiveOrigin();
        if (activeOrigin == null || activeOrigin.isBlank()) {
            return AdminFileResult.error(503, "No active origin available");
        }
        return originAdminGateway.getFileMetadata(activeOrigin, path);
    }

    private ReplicationResult replicateUpload(String path, byte[] body) {
        return replicateToSpares(spare -> originAdminGateway.uploadFile(spare, path, body));
    }

    private ReplicationResult replicateDelete(String path) {
        return replicateToSpares(spare -> originAdminGateway.deleteFile(spare, path));
    }

    private ReplicationResult replicateToSpares(java.util.function.Function<String, AdminFileResult> operation) {
        List<String> spares = originClusterService.spareOriginsSnapshot();
        if (spares.isEmpty()) {
            return new ReplicationResult(0, 0, 0);
        }

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        List<CompletableFuture<Void>> futures = spares.stream()
                .map(spare -> CompletableFuture.runAsync(() -> {
                            AdminFileResult result = operation.apply(spare);
                            if (result.success()) {
                                success.incrementAndGet();
                            } else {
                                failed.incrementAndGet();
                            }
                        })
                        .orTimeout(5, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            failed.incrementAndGet();
                            return null;
                        }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return new ReplicationResult(spares.size(), success.get(), failed.get());
    }

    private record ReplicationResult(int totalSpares, int replicated, int failed) {
        private Map<String, Object> toMap() {
            return Map.of("totalSpares", totalSpares, "replicated", replicated, "failed", failed);
        }
    }
}
