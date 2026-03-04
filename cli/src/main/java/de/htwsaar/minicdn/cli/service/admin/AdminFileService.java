package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Admin-File-Service ausschließlich über den Router.
 *
 * Erwartete Router-Admin-APIs:
 *   - PUT    /api/cdn/admin/files/{path}?region=REGION    (Upload + Cache-Invalidation)
 *   - DELETE /api/cdn/admin/files/{path}?region=REGION    (Delete + Cache-Invalidation)
 *   - GET    /api/cdn/admin/files?page=&size=             (List, Router fragt Origin)
 *   - GET    /api/cdn/admin/files/{path}                  (Metadata als JSON)
 *
 * Download nutzt den normalen User-Path:
 *   - GET    /api/cdn/files/{path}?region=REGION          (307 Redirect zu Edge)
 */
public final class AdminFileService {

    private static final String HEADER_REGION =
            "X-Client-Region"; // Admin-Downloads nutzen den gleichen Header wie User-Downloads, damit sie in der
    // gleichen Statistik landen (ohne clientId)

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    public AdminFileService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Upload via Router-Admin-API:
     *   PUT /api/cdn/admin/files/{path}?region=REGION
     */
    public HttpCallResult uploadViaRouter(URI routerBaseUrl, String targetPath, Path localFile, String region) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(localFile, "localFile");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("targetPath must not be blank");
        }
        String cleanRegion = Objects.toString(region, "").trim();
        if (cleanRegion.isBlank()) {
            return HttpCallResult.clientError("region must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        String pathAndQuery = "/api/cdn/admin/files/" + cleanPath + "?region=" + JsonUtils.urlEncode(cleanRegion);

        URI url = base.resolve(pathAndQuery);

        TransportResponse response = transportClient.send(TransportRequest.putFile(
                url,
                requestTimeout,
                Map.of("X-Admin-Token", resolveAdminToken(), "Content-Type", "application/octet-stream"),
                localFile));
        return toHttpCallResult(response);
    }

    /**
     * Delete via Router-Admin-API:
     *   DELETE /api/cdn/admin/files/{path}?region=REGION
     */
    public HttpCallResult deleteViaRouter(URI routerBaseUrl, String targetPath, String region) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("path must not be blank");
        }
        String cleanRegion = Objects.toString(region, "").trim();
        if (cleanRegion.isBlank()) {
            return HttpCallResult.clientError("region must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        String pathAndQuery = "/api/cdn/admin/files/" + cleanPath + "?region=" + JsonUtils.urlEncode(cleanRegion);

        URI url = base.resolve(pathAndQuery);

        TransportResponse response = transportClient.send(
                TransportRequest.delete(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));
        return toHttpCallResult(response);
    }

    /**
     * Listet Dateien über den Router:
     *   GET /api/cdn/admin/files?page=&size=
     */
    public HttpCallResult listViaRouter(URI routerBaseUrl, int page, int size) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        if (page < 1) {
            return HttpCallResult.clientError("page must be >= 1");
        }
        if (size <= 0) {
            return HttpCallResult.clientError("size must be > 0");
        }

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve(String.format("/api/cdn/admin/files?page=%d&size=%d", page, size));

        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));
        return toHttpCallResult(response);
    }

    /**
     * Liefert Metadaten zu einer Datei über den Router:
     *   GET /api/cdn/admin/files/{path}
     *  Erwartet JSON-Antwort vom Router (z. B. contentType, size, sha256, lastModified, ...)
     */
    public HttpCallResult showViaRouter(URI routerBaseUrl, String targetPath) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("path must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("/api/cdn/admin/files/" + cleanPath);

        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));
        return toHttpCallResult(response);
    }

    private static HttpCallResult toHttpCallResult(TransportResponse response) {
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
