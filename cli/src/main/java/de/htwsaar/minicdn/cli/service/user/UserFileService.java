package de.htwsaar.minicdn.cli.service.user;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Datei-Downloads über den Router.
 *
 * <p>Implementiert weiterhin den Router-Redirect-Flow, kennt aber keine konkreten
 * HTTP-Klassen mehr.
 */
public final class UserFileService {

    private static final String HEADER_REGION = "X-Client-Region";
    private static final String HEADER_CLIENT_ID = "X-Client-Id";

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    public UserFileService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Download über Router inkl. Redirect-Following.
     */
    public DownloadResult downloadViaRouter(
            URI routerBaseUrl,
            String remotePath,
            String region,
            String clientId,
            java.nio.file.Path out,
            boolean overwrite) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(remotePath, "remotePath");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(out, "out");

        if (region.isBlank()) {
            return DownloadResult.ioError("region must not be blank");
        }

        URI routingUri = routerBaseUrl.resolve("api/cdn/files/" + remotePath);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_REGION, region.trim());
        if (clientId != null && !clientId.isBlank()) {
            headers.put(HEADER_CLIENT_ID, clientId.trim());
        }

        TransportResponse routingResponse =
                transportClient.send(TransportRequest.get(routingUri, requestTimeout, headers));

        if (routingResponse.error() != null) {
            return DownloadResult.ioError(routingResponse.error());
        }

        int statusCode = Objects.requireNonNull(routingResponse.statusCode(), "statusCode");

        if (statusCode >= 200 && statusCode < 300) {
            return transportClient.download(TransportRequest.get(routingUri, requestTimeout, headers), out, overwrite);
        }

        if (statusCode == 307 || statusCode == 308) {
            String location = routingResponse.firstHeader("Location");
            if (location == null || location.isBlank()) {
                return DownloadResult.ioError("router redirect missing Location header");
            }

            URI edgeUri = routingUri.resolve(location);
            return transportClient.download(TransportRequest.get(edgeUri, requestTimeout, Map.of()), out, overwrite);
        }

        return DownloadResult.httpError(statusCode);
    }
}
