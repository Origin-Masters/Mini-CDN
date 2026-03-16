package de.htwsaar.minicdn.cli.application.user;

import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.model.RemoteFileProbe;
import de.htwsaar.minicdn.cli.domain.model.ResolvedFileRoute;
import de.htwsaar.minicdn.cli.domain.port.UserFileTransfers;
import de.htwsaar.minicdn.common.util.PathUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fachlicher Service für Datei-Downloads über den Router.
 *
 * <p>Segmentierung, Retry und Zusammensetzen der Segmente bleiben hier. Alle
 * transportabhängigen Details der konkreten Remote-Anbindung
 * liegen vollständig im Outbound-Adapter.</p>
 */
public final class UserFileService {

    private final UserFileTransfers userFileTransfers;

    /**
     * Erzeugt den Download-Service.
     *
     * @param userFileTransfers fachlicher Port für Dateiübertragungen
     */
    public UserFileService(UserFileTransfers userFileTransfers) {
        this.userFileTransfers = Objects.requireNonNull(userFileTransfers, "userFileTransfers");
    }

    /**
     * Lädt eine Datei über den Router herunter.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath relativer Remote-Pfad der Datei
     * @param region Client-Region für das Routing
     * @param clientId optionale Client-ID für Statistikzwecke
     * @param out lokale Zieldatei
     * @param overwrite {@code true}, wenn eine bestehende Datei überschrieben werden darf
     * @return normiertes Download-Ergebnis
     */
    public DownloadResult downloadViaRouter(
            URI routerBaseUrl, String remotePath, String region, String clientId, Path out, boolean overwrite) {
        return downloadViaRouter(routerBaseUrl, remotePath, region, clientId, null, out, overwrite);
    }

    /**
     * Lädt eine Datei segmentiert und parallel von mehreren Edge-Knoten.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath relativer Remote-Pfad
     * @param region Client-Region
     * @param clientId optionale Client-ID
     * @param userId optionale User-ID
     * @param out lokale Ausgabedatei
     * @param overwrite bestehende Datei überschreiben
     * @param segmentCount Anzahl Segmente
     * @param maxRetries maximale Wiederholversuche je Segment
     * @param preferredEdgeBaseUrls optionale feste Edge-Ziele; wenn leer, wird pro Segment geroutet
     * @return Download-Ergebnis
     */
    public DownloadResult downloadSegmentedViaEdges(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            Long userId,
            Path out,
            boolean overwrite,
            int segmentCount,
            int maxRetries,
            List<URI> preferredEdgeBaseUrls) {

        Objects.requireNonNull(out, "out");
        String cleanRemotePath = normalizeRemotePath(remotePath);
        String cleanRegion = requireText(region, "region");
        int cleanSegmentCount = Math.max(1, segmentCount);
        int cleanRetries = Math.max(0, maxRetries);

        try {
            long totalSize = probeFileSize(routerBaseUrl, cleanRemotePath, cleanRegion, clientId, userId);
            List<SegmentPlan> plans = splitIntoSegments(totalSize, cleanSegmentCount);
            List<ResolvedFileRoute> edgeLocations = resolveEdgeLocations(
                    routerBaseUrl, cleanRemotePath, cleanRegion, clientId, userId, preferredEdgeBaseUrls, plans.size());

            Path tempDir = Files.createTempDirectory("minicdn-segments-");
            try {
                List<Path> segmentFiles = fetchSegmentsParallel(
                        plans, edgeLocations, cleanRemotePath, tempDir, cleanRetries, cleanRegion, clientId, userId);

                assembleSegments(segmentFiles, out, overwrite);
                return DownloadResult.success(200, Files.size(out));
            } finally {
                cleanupDirectory(tempDir);
            }
        } catch (Exception ex) {
            return DownloadResult.ioError(ex.getMessage());
        }
    }

    private long probeFileSize(URI routerBaseUrl, String remotePath, String region, String clientId, Long userId) {
        ResolvedFileRoute route = userFileTransfers.resolveRoute(routerBaseUrl, remotePath, region, clientId, userId);
        RemoteFileProbe probe = userFileTransfers.probeRemoteFile(route);
        return probe.totalLength();
    }

    private List<ResolvedFileRoute> resolveEdgeLocations(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            Long userId,
            List<URI> preferredEdgeBaseUrls,
            int segmentCount) {

        if (preferredEdgeBaseUrls != null && !preferredEdgeBaseUrls.isEmpty()) {
            List<ResolvedFileRoute> resolved = new ArrayList<>(segmentCount);
            for (int i = 0; i < segmentCount; i++) {
                resolved.add(ResolvedFileRoute.of(preferredEdgeBaseUrls.get(i % preferredEdgeBaseUrls.size())));
            }
            return resolved;
        }

        List<ResolvedFileRoute> resolved = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            resolved.add(userFileTransfers.resolveRoute(routerBaseUrl, remotePath, region, clientId, userId));
        }
        return resolved;
    }

    private List<Path> fetchSegmentsParallel(
            List<SegmentPlan> plans,
            List<ResolvedFileRoute> edgeLocations,
            String remotePath,
            Path tempDir,
            int retries,
            String region,
            String clientId,
            Long userId)
            throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, plans.size()));
        try {
            List<Future<Path>> futures = new ArrayList<>(plans.size());
            for (int i = 0; i < plans.size(); i++) {
                SegmentPlan plan = plans.get(i);
                ResolvedFileRoute route = edgeLocations.get(i);
                futures.add(executor.submit(
                        () -> fetchSingleSegment(plan, route, tempDir, retries, region, clientId, userId)));
            }

            List<Path> files = new ArrayList<>(plans.size());
            for (Future<Path> future : futures) {
                files.add(future.get());
            }
            files.sort(Comparator.comparing(Path::toString));
            return files;
        } catch (Exception ex) {
            throw new IllegalStateException("segmented download failed for " + remotePath + ": " + ex.getMessage(), ex);
        } finally {
            executor.shutdownNow();
        }
    }

    private Path fetchSingleSegment(
            SegmentPlan plan,
            ResolvedFileRoute route,
            Path tempDir,
            int retries,
            String region,
            String clientId,
            Long userId)
            throws IOException {

        Path partPath = tempDir.resolve(String.format("part-%05d.bin", plan.index()));

        for (int attempt = 0; attempt <= retries; attempt++) {
            DownloadResult result = userFileTransfers.downloadSegment(
                    route, plan.start(), plan.end(), region, clientId, userId, partPath);
            if (!result.isSuccess()) {
                continue;
            }

            long expectedLength = plan.end() - plan.start() + 1;
            long actualLength = Files.size(partPath);
            if (actualLength == expectedLength) {
                return partPath;
            }
        }

        throw new IllegalStateException("segment " + plan.index() + " failed after retries");
    }

    private static void assembleSegments(List<Path> segmentFiles, Path out, boolean overwrite) throws IOException {
        if (Files.exists(out) && !overwrite) {
            throw new IOException("output file exists");
        }

        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (overwrite) {
            Files.deleteIfExists(out);
        }
        for (Path segmentFile : segmentFiles) {
            byte[] bytes = Files.readAllBytes(segmentFile);
            Files.write(out, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static void cleanupDirectory(Path directory) {
        try (var files = Files.list(directory)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Cleanup-Fehler sind unkritisch.
                }
            });
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // Cleanup-Fehler sind unkritisch.
        }
    }

    /**
     * Segmentplan für einen Teilbereich der Datei.
     *
     * @param index Segmentindex
     * @param start Startbyte inklusiv
     * @param end Endbyte inklusiv
     */
    record SegmentPlan(int index, long start, long end) {}

    /**
     * Teilt die Gesamtgröße in gleichmäßige Byte-Segmente auf.
     *
     * @param totalSize Dateigröße
     * @param segmentCount gewünschte Segmentanzahl
     * @return geordneter Segmentplan
     */
    static List<SegmentPlan> splitIntoSegments(long totalSize, int segmentCount) {
        if (totalSize <= 0) {
            throw new IllegalArgumentException("totalSize must be > 0");
        }

        int effectiveSegments = (int) Math.min(Math.max(1, segmentCount), totalSize);
        long segmentSize = totalSize / effectiveSegments;
        long remainder = totalSize % effectiveSegments;

        List<SegmentPlan> plans = new ArrayList<>(effectiveSegments);
        long start = 0;
        for (int i = 0; i < effectiveSegments; i++) {
            long size = segmentSize + (i < remainder ? 1 : 0);
            long end = start + size - 1;
            plans.add(new SegmentPlan(i, start, end));
            start = end + 1;
        }
        return plans;
    }

    /**
     * Lädt eine Datei über den Router herunter und übergibt optional die eingeloggte User-ID.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath relativer Remote-Pfad der Datei
     * @param region Client-Region für das Routing
     * @param clientId optionale Client-ID für Statistikzwecke
     * @param userId optionale technische User-ID
     * @param out lokale Zieldatei
     * @param overwrite {@code true}, wenn eine bestehende Datei überschrieben werden darf
     * @return normiertes Download-Ergebnis
     */
    public DownloadResult downloadViaRouter(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            Long userId,
            Path out,
            boolean overwrite) {

        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(out, "out");

        try {
            return userFileTransfers.downloadViaRouter(
                    routerBaseUrl,
                    normalizeRemotePath(remotePath),
                    requireText(region, "region"),
                    clientId,
                    userId,
                    out,
                    overwrite);
        } catch (Exception ex) {
            return DownloadResult.ioError(ex.getMessage());
        }
    }

    private static String normalizeRemotePath(String remotePath) {
        return PathUtils.normalizeRelativePath(remotePath);
    }

    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
