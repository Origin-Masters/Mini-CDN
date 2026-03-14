package de.htwsaar.minicdn.cli.service.user;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportClientFactory;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fachlicher Service für Datei-Downloads über den Router.
 *
 * <p>Die Klasse implementiert den fachlichen Download-Flow über den Router.
 * Transport-spezifische Details wie Redirect-Following liegen im Transportadapter.
 * Sie enthält keine CLI-Ausgabe und keine Exit-Code-Logik.</p>
 */
public final class UserFileService {

    private static final String HEADER_REGION = "X-Client-Region";
    private static final String HEADER_CLIENT_ID = "X-Client-Id";
    private static final String HEADER_USER_ID = "X-User-Id";

    private final TransportClient transportClient;
    private final TransportClient nonRedirectTransportClient;
    private final Duration requestTimeout;

    /**
     * Erzeugt den Download-Service.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     */
    public UserFileService(TransportClient transportClient, Duration requestTimeout) {
        this(transportClient, TransportClientFactory.http(requestTimeout, false), requestTimeout);
    }

    /**
     * Erzeugt den Download-Service mit explizitem Transportadapter für Redirect-freie Route-Requests.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param nonRedirectTransportClient Transportadapter ohne Redirect-Following
     * @param requestTimeout Standard-Timeout für Requests
     */
    public UserFileService(
            TransportClient transportClient, TransportClient nonRedirectTransportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.nonRedirectTransportClient =
                Objects.requireNonNull(nonRedirectTransportClient, "nonRedirectTransportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /** Headername für Byte-Range-Requests. */
    private static final String HEADER_RANGE = "Range";

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
     * @param segmentCount Anzahl Segmente/Parallelität
     * @param maxRetries maximale Wiederholversuche je Segment
     * @param preferredEdgeBaseUrls optionale Edge-Basis-URLs; wenn leer, wird pro Segment eine Route vom Router geholt
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
            List<URI> edgeLocations = resolveEdgeLocations(
                    routerBaseUrl, cleanRemotePath, cleanRegion, clientId, userId, preferredEdgeBaseUrls, plans.size());

            Path tempDir = Files.createTempDirectory("minicdn-segments-");
            try {
                List<Path> segmentFiles = fetchSegmentsParallel(
                        plans, edgeLocations, cleanRemotePath, tempDir, cleanRetries, cleanRegion, clientId, userId);

                assembleSegments(segmentFiles, out, overwrite);
                return DownloadResult.ok(200, Files.size(out));
            } finally {
                cleanupDirectory(tempDir);
            }
        } catch (Exception ex) {
            return DownloadResult.ioError(ex.getMessage());
        }
    }

    /**
     * Ermittelt die Gesamtgröße der Datei über einen initialen Byte-Range-Probe-Request.
     */
    private long probeFileSize(URI routerBaseUrl, String remotePath, String region, String clientId, Long userId) {
        URI routingUri = routingUri(routerBaseUrl, remotePath);
        Map<String, String> headers = new LinkedHashMap<>(routingHeaders(region, clientId, userId));
        headers.put(HEADER_RANGE, "bytes=0-0");

        TransportRequest request = TransportRequest.get(routingUri, requestTimeout, headers);
        TransportResponse response = nonRedirectTransportClient.send(request);
        if (response.error() != null) {
            throw new IllegalStateException("cannot resolve route for segmented download: " + response.error());
        }

        String location = firstHeader(response, "location");
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("router did not return an edge location");
        }

        URI edgeUri = URI.create(location);
        String contentRange = readContentRange(edgeUri);
        return parseTotalLengthFromContentRange(contentRange);
    }

    /**
     * Liest den {@code Content-Range}-Header einer Edge-Ressource.
     */
    private String readContentRange(URI edgeUri) {
        TransportResponse response = nonRedirectTransportClient.send(
                TransportRequest.get(edgeUri, requestTimeout, Map.of(HEADER_RANGE, "bytes=0-0")));
        if (response.error() != null) {
            throw new IllegalStateException("probe metadata failed: " + response.error());
        }
        if (response.statusCode() == null || (response.statusCode() != 206 && response.statusCode() != 200)) {
            throw new IllegalStateException("probe metadata returned HTTP " + response.statusCode());
        }
        String contentRange = firstHeader(response, "content-range");
        if (contentRange == null || contentRange.isBlank()) {
            throw new IllegalStateException("edge did not return content-range");
        }
        return contentRange;
    }

    /**
     * Parst die Gesamtlänge aus einem {@code Content-Range}-Header.
     */
    private static long parseTotalLengthFromContentRange(String contentRange) {
        int slashIndex = contentRange.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= contentRange.length()) {
            throw new IllegalArgumentException("invalid content-range: " + contentRange);
        }
        return Long.parseLong(contentRange.substring(slashIndex + 1).trim());
    }

    /**
     * Ermittelt je Segment ein Edge-Download-Ziel.
     */
    private List<URI> resolveEdgeLocations(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            Long userId,
            List<URI> preferredEdgeBaseUrls,
            int segmentCount) {

        if (preferredEdgeBaseUrls != null && !preferredEdgeBaseUrls.isEmpty()) {
            List<URI> locations = new ArrayList<>(segmentCount);
            for (int i = 0; i < segmentCount; i++) {
                URI edgeBase =
                        UriUtils.ensureTrailingSlash(preferredEdgeBaseUrls.get(i % preferredEdgeBaseUrls.size()));
                locations.add(edgeBase.resolve("api/edge/files/" + remotePath));
            }
            return locations;
        }

        URI routingUri = routingUri(routerBaseUrl, remotePath);
        Map<String, String> headers = routingHeaders(region, clientId, userId);

        List<URI> locations = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            TransportResponse response =
                    nonRedirectTransportClient.send(TransportRequest.get(routingUri, requestTimeout, headers));
            if (response.error() != null) {
                throw new IllegalStateException("routing failed: " + response.error());
            }
            String location = firstHeader(response, "location");
            if (location == null || location.isBlank()) {
                throw new IllegalStateException("router returned no location");
            }
            locations.add(URI.create(location));
        }
        return locations;
    }

    /**
     * Lädt alle Segmente parallel und liefert die temporären Segmentdateien zurück.
     */
    private List<Path> fetchSegmentsParallel(
            List<SegmentPlan> plans,
            List<URI> locations,
            String remotePath,
            Path tempDir,
            int retries,
            String region,
            String clientId,
            Long userId) {

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(plans.size(), 8));
        try {
            List<Future<Path>> futures = new ArrayList<>();
            for (int i = 0; i < plans.size(); i++) {
                SegmentPlan plan = plans.get(i);
                URI location = locations.get(i);
                futures.add(executor.submit(
                        () -> fetchSingleSegment(plan, location, tempDir, retries, region, clientId, userId)));
            }

            List<Path> files = new ArrayList<>();
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

    /**
     * Lädt ein einzelnes Segment mit Retry bei invaliden oder fehlgeschlagenen Antworten.
     */
    private Path fetchSingleSegment(
            SegmentPlan plan, URI location, Path tempDir, int retries, String region, String clientId, Long userId)
            throws IOException {

        Path partPath = tempDir.resolve(String.format("part-%05d.bin", plan.index()));
        Map<String, String> headers = new LinkedHashMap<>(routingHeaders(region, clientId, userId));
        headers.put(HEADER_RANGE, "bytes=" + plan.start() + "-" + plan.end());
        TransportRequest request = TransportRequest.get(location, requestTimeout, headers);

        for (int attempt = 0; attempt <= retries; attempt++) {
            DownloadResult result = transportClient.download(request, partPath, true);
            if (result.error() != null) {
                continue;
            }

            long expectedLength = plan.end() - plan.start() + 1;
            long actualLength = Files.size(partPath);
            boolean validStatus = Integer.valueOf(206).equals(result.statusCode())
                    || (plan.start() == 0 && Integer.valueOf(200).equals(result.statusCode()));
            if (validStatus && actualLength == expectedLength) {
                return partPath;
            }
        }

        throw new IllegalStateException("segment " + plan.index() + " failed after retries");
    }

    /**
     * Setzt geladene Segmente in korrekter Reihenfolge zur Ausgabedatei zusammen.
     */
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

    /**
     * Entfernt temporäre Segmentdateien und das zugehörige Arbeitsverzeichnis.
     */
    private static void cleanupDirectory(Path directory) {
        try (var files = Files.list(directory)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // ignore cleanup errors
                }
            });
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // ignore cleanup errors
        }
    }

    /**
     * Liest den ersten Headerwert für einen Headernamen in kleingeschriebener Form.
     */
    private static String firstHeader(TransportResponse response, String name) {
        return response.headers().getOrDefault(name.toLowerCase(), List.of()).stream()
                .findFirst()
                .orElse(null);
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

        String cleanRemotePath = normalizeRemotePath(remotePath);
        String cleanRegion = requireText(region, "region");
        URI routingUri = routingUri(routerBaseUrl, cleanRemotePath);
        Map<String, String> routingHeaders = routingHeaders(cleanRegion, clientId, userId);
        TransportRequest routingRequest = TransportRequest.get(routingUri, requestTimeout, routingHeaders);

        try {
            return transportClient.download(routingRequest, out, overwrite);
        } catch (Exception ex) {
            return DownloadResult.ioError(ex.getMessage());
        }
    }

    /**
     * Baut die Router-Download-URL aus Basis-URL und Remote-Pfad.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param cleanRemotePath validierter relativer Remote-Pfad
     * @return vollständige Router-Download-URL
     */
    private static URI routingUri(URI routerBaseUrl, String cleanRemotePath) {
        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        return base.resolve("api/cdn/files/" + cleanRemotePath);
    }

    /**
     * Baut die Header für den Router-Download-Request.
     *
     * @param region validierte Region
     * @param clientId optionale Client-ID
     * @return Header-Map für den Request
     */
    private static Map<String, String> routingHeaders(String region, String clientId, Long userId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_REGION, region);

        if (hasText(clientId)) {
            headers.put(HEADER_CLIENT_ID, clientId.trim());
        }

        if (userId != null && userId > 0) {
            headers.put(HEADER_USER_ID, String.valueOf(userId));
        }

        return headers;
    }

    /**
     * Validiert und normalisiert einen relativen Remote-Pfad.
     *
     * @param remotePath roher Remote-Pfad
     * @return normalisierter relativer Pfad
     */
    private static String normalizeRemotePath(String remotePath) {
        return PathUtils.normalizeRelativePath(remotePath);
    }

    /**
     * Validiert einen Pflichttext und liefert die getrimmte Form zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    /**
     * Prüft, ob ein Text gesetzt ist.
     *
     * @param value zu prüfender Text
     * @return {@code true}, wenn der Text nicht leer ist
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
