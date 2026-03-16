package de.htwsaar.minicdn.cli.adapter.out.http;

import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.model.RemoteFileProbe;
import de.htwsaar.minicdn.cli.domain.model.ResolvedFileRoute;
import de.htwsaar.minicdn.cli.domain.port.UserFileTransfers;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-Adapter für Dateiübertragungen der CLI.
 */
public final class HttpUserFileTransfers implements UserFileTransfers {

    private static final String HEADER_REGION = "X-Client-Region";
    private static final String HEADER_CLIENT_ID = "X-Client-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_RANGE = "Range";

    private final TransportClient transportClient;
    private final TransportClient nonRedirectTransportClient;
    private final Duration requestTimeout;

    /**
     * Erzeugt den Adapter mit Default-Transporten für Redirect und Non-Redirect.
     *
     * @param transportClient Standard-Transport mit Redirect-Following
     * @param requestTimeout Standard-Timeout
     */
    public HttpUserFileTransfers(TransportClient transportClient, Duration requestTimeout) {
        this(transportClient, TransportClientFactory.http(requestTimeout, false), requestTimeout);
    }

    /**
     * Erzeugt den Adapter mit expliziten Transporten.
     *
     * @param transportClient Standard-Transport mit Redirect-Following
     * @param nonRedirectTransportClient Transport ohne Redirect-Following
     * @param requestTimeout Standard-Timeout
     */
    public HttpUserFileTransfers(
            TransportClient transportClient, TransportClient nonRedirectTransportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.nonRedirectTransportClient =
                Objects.requireNonNull(nonRedirectTransportClient, "nonRedirectTransportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /** {@inheritDoc} */
    @Override
    public DownloadResult downloadViaRouter(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            Long userId,
            Path out,
            boolean overwrite) {
        URI routingUri = routingUri(routerBaseUrl, remotePath);
        return transportClient.download(
                TransportRequest.get(routingUri, requestTimeout, routingHeaders(region, clientId, userId)),
                out,
                overwrite);
    }

    /** {@inheritDoc} */
    @Override
    public ResolvedFileRoute resolveRoute(
            URI routerBaseUrl, String remotePath, String region, String clientId, Long userId) {
        TransportResponse response = nonRedirectTransportClient.send(TransportRequest.get(
                routingUri(routerBaseUrl, remotePath), requestTimeout, routingHeaders(region, clientId, userId)));
        if (response.error() != null) {
            throw new IllegalStateException("cannot resolve route: " + response.error());
        }
        String location = response.firstHeader("location");
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("router did not return a target location");
        }
        return ResolvedFileRoute.of(URI.create(location));
    }

    /** {@inheritDoc} */
    @Override
    public RemoteFileProbe probeRemoteFile(ResolvedFileRoute route) {
        URI targetUri = requireRoute(route);
        TransportResponse response = nonRedirectTransportClient.send(
                TransportRequest.get(targetUri, requestTimeout, Map.of(HEADER_RANGE, "bytes=0-0")));
        if (response.error() != null) {
            throw new IllegalStateException("probe metadata failed: " + response.error());
        }
        Integer statusCode = response.statusCode();
        if (statusCode == null || (statusCode != 200 && statusCode != 206)) {
            throw new IllegalStateException("probe metadata failed");
        }
        String contentRange = response.firstHeader("content-range");
        if (contentRange == null || contentRange.isBlank()) {
            throw new IllegalStateException("remote target did not return content-range");
        }
        return RemoteFileProbe.of(parseTotalLength(contentRange));
    }

    /** {@inheritDoc} */
    @Override
    public DownloadResult downloadSegment(
            ResolvedFileRoute route,
            long startInclusive,
            long endInclusive,
            String region,
            String clientId,
            Long userId,
            Path out) {
        URI targetUri = requireRoute(route);
        Map<String, String> headers = new LinkedHashMap<>(routingHeaders(region, clientId, userId));
        headers.put(HEADER_RANGE, "bytes=" + startInclusive + "-" + endInclusive);
        DownloadResult result =
                transportClient.download(TransportRequest.get(targetUri, requestTimeout, headers), out, true);
        if (!result.isSuccess()) {
            return result;
        }

        long expectedLength = endInclusive - startInclusive + 1;
        boolean acceptableStatus = Integer.valueOf(206).equals(result.code())
                || (startInclusive == 0 && Integer.valueOf(200).equals(result.code()));
        if (!acceptableStatus) {
            return DownloadResult.rejected(416);
        }

        try {
            return Files.size(out) == expectedLength ? result : DownloadResult.ioError("segment length mismatch");
        } catch (Exception ex) {
            return DownloadResult.ioError(ex.getMessage());
        }
    }

    /**
     * Liest die technische Ziel-URI aus einem fachlichen Routing-Ergebnis.
     *
     * <p>Die Download-Orchestrierung kann dadurch mit dem fachlichen
     * {@link ResolvedFileRoute} arbeiten, ohne URI-Details selbst zu verarbeiten.</p>
     *
     * @param route fachlich aufgelöstes Datei-Ziel
     * @return technische Ziel-URI
     */
    private static URI requireRoute(ResolvedFileRoute route) {
        Objects.requireNonNull(route, "route");
        return route.targetUri();
    }

    /**
     * Baut die Router-API-URL für einen relativen Datei-Pfad.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath Relativer Datei-Pfad
     * @return Vollständige URI auf den Datei-Endpunkt des Routers
     */
    private static URI routingUri(URI routerBaseUrl, String remotePath) {
        String cleanRemotePath = PathUtils.normalizeRelativePath(remotePath);
        return UriUtils.ensureTrailingSlash(routerBaseUrl).resolve("api/cdn/files/" + cleanRemotePath);
    }

    /**
     * Erzeugt Routing-Header mit Pflichtangabe für Region und optionalen Identitäten.
     *
     * @param region Client-Region
     * @param clientId Optionale Client-ID
     * @param userId Optionale User-ID
     * @return Header-Map für Router- und Edge-Aufrufe
     */
    private static Map<String, String> routingHeaders(String region, String clientId, Long userId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_REGION, HttpAdapterSupport.requireText(region, "region"));
        if (clientId != null && !clientId.isBlank()) {
            headers.put(HEADER_CLIENT_ID, clientId.trim());
        }
        if (userId != null && userId > 0) {
            headers.put(HEADER_USER_ID, String.valueOf(userId));
        }
        return headers;
    }

    /**
     * Liest aus einem {@code Content-Range}-Header die Gesamtgröße der Ressource.
     *
     * @param contentRange Wert des {@code Content-Range}-Headers
     * @return Gesamtlänge der Ressource in Byte
     * @throws IllegalArgumentException wenn der Header nicht dem erwarteten Format entspricht
     */
    private static long parseTotalLength(String contentRange) {
        int slashIndex = contentRange.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= contentRange.length()) {
            throw new IllegalArgumentException("invalid content-range: " + contentRange);
        }
        return Long.parseLong(contentRange.substring(slashIndex + 1).trim());
    }
}
