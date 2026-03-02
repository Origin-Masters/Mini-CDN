package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class AdminFileService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    public AdminFileService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Lade eine lokale Datei auf den Origin-Server hoch (Admin-API): PUT /api/origin/admin/files/{path}
     */
    public HttpCallResult uploadToOrigin(URI originBaseUrl, String targetPath, Path localFile)
            throws FileNotFoundException {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");
        Objects.requireNonNull(localFile, "localFile");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("targetPath must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve("api/origin/admin/files/" + cleanPath);

        TransportResponse response = transportClient.send(TransportRequest.putFile(
                url,
                requestTimeout,
                Map.of("X-Admin-Token", resolveAdminToken(), "Content-Type", "application/octet-stream"),
                localFile));

        return toHttpCallResult(response);
    }

    /**
     * Liste alle Dateien auf dem Origin-Server auf (Admin-API): GET /api/origin/files?page={page}&size={size}
     */
    public HttpCallResult listOriginFiles(URI originBaseUrl, int page, int size) {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        if (page < 1) {
            return HttpCallResult.clientError("page must be >= 1");
        }
        if (size <= 0) {
            return HttpCallResult.clientError("size must be > 0");
        }

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve(String.format("api/origin/files?page=%d&size=%d", page, size));

        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));

        return toHttpCallResult(response);
    }

    /**
     * Zeige Metadaten einer Datei auf dem Origin-Server an (Admin-API): HEAD /api/origin/files/{path}
     */
    public HttpCallResult showOriginFile(URI originBaseUrl, String targetPath) {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("path must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve("api/origin/files/" + cleanPath);

        TransportResponse response = transportClient.send(
                TransportRequest.head(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));

        if (response.error() != null) {
            return HttpCallResult.ioError(response.error());
        }

        String len = response.firstHeader("Content-Length");
        String type = response.firstHeader("Content-Type");
        String sha = response.firstHeader("X-Content-SHA256");

        String json = String.format(
                "{\"path\":\"%s\",\"size\":%s,\"contentType\":%s,\"sha256\":%s}",
                JsonUtils.escapeJson(cleanPath),
                len == null ? "null" : len,
                type == null ? "null" : "\"" + JsonUtils.escapeJson(type) + "\"",
                sha == null ? "null" : "\"" + JsonUtils.escapeJson(sha) + "\"");

        return HttpCallResult.http(Objects.requireNonNull(response.statusCode(), "statusCode"), json);
    }

    /**
     * Lade eine Datei vom Origin-Server herunter und speichere sie lokal (Admin-API): GET /api/origin/files/{path}
     */
    public HttpCallResult downloadOriginFile(URI originBaseUrl, String targetPath, Path localTargetFile) {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");
        Objects.requireNonNull(localTargetFile, "localTargetFile");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("path must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve("api/origin/files/" + cleanPath);

        DownloadResult result = transportClient.download(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())),
                localTargetFile,
                true);

        if (result.error() != null) {
            return HttpCallResult.ioError(result.error());
        }

        return HttpCallResult.http(
                Objects.requireNonNull(result.statusCode(), "statusCode"), localTargetFile.toString());
    }

    /**
     * Lösche eine Datei auf dem Origin-Server (Admin-API): DELETE /api/origin/admin/files/{path}
     */
    public HttpCallResult deleteOriginFile(URI origin, String cleanPath) {
        Objects.requireNonNull(origin, "origin");

        String path = PathUtils.stripLeadingSlash(Objects.toString(cleanPath, ""));
        if (path.isBlank()) {
            return HttpCallResult.clientError("path must not be blank");
        }

        URI base = UriUtils.ensureTrailingSlash(origin);
        URI url = base.resolve("api/origin/admin/files/" + path);

        TransportResponse response = transportClient.send(
                TransportRequest.delete(url, requestTimeout, Map.of("X-Admin-Token", resolveAdminToken())));

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
