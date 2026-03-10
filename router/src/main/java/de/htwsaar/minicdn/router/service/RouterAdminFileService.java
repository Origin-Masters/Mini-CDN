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

/**
 * Fachlicher Service für Admin-Dateioperationen über den Router.
 *
 * <p>Schreiboperationen werden an den Origin delegiert.
 * Die anschließende Cache-Invalidierung nutzt nur fachliche Edge-Operationen.</p>
 */
@Service
public class RouterAdminFileService {

    private final OriginAdminGateway originAdminGateway;
    private final RoutingIndex routingIndex;
    private final EdgeGateway edgeGateway;

    public RouterAdminFileService(
            OriginAdminGateway originAdminGateway, RoutingIndex routingIndex, EdgeGateway edgeGateway) {

        this.originAdminGateway = originAdminGateway;
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
            AdminFileResult uploadResult = originAdminGateway.uploadFile(path, body);
            if (!uploadResult.success()) {
                return uploadResult;
            }

            int invalidated = invalidateCaches(path, region);

            return AdminFileResult.success(
                    201, Map.of("uploaded", true, "path", path, "size", body.length, "edgesInvalidated", invalidated));
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
            AdminFileResult deleteResult = originAdminGateway.deleteFile(path);
            if (!deleteResult.success()) {
                return deleteResult;
            }

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
     * Listet Dateien aus dem Origin auf.
     *
     * @param page Seitennummer
     * @param size Seitengröße
     * @return Ergebnis der Origin-Abfrage
     */
    public AdminFileResult listOriginFiles(int page, int size) {
        return originAdminGateway.listFiles(page, size);
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
}
