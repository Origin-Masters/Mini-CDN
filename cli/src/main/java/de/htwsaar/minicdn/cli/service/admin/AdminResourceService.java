package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.util.HttpUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class AdminResourceService {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public AdminResourceService(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
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

        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(localFile))
                .build();

        return HttpUtils.sendForStringBody(httpClient, req);
    }

    /**
     * Liste alle Dateien auf dem Origin-Server auf (Admin-API): GET /api/origin/files?page={page}&size={size}
     */
    public HttpCallResult listOriginFiles(URI originBaseUrl, int page, int size) {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");
        if (page < 1) return HttpCallResult.clientError("page must be >= 1");
        if (size <= 0) return HttpCallResult.clientError("size must be > 0");

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve(String.format("api/origin/files?page=%d&size=%d", page, size));

        HttpRequest req =
                HttpRequest.newBuilder(url).timeout(requestTimeout).GET().build();

        return HttpUtils.sendForStringBody(httpClient, req);
    }

    /**
     * Zeige Metadaten einer Datei auf dem Origin-Server an (Admin-API): HEAD /api/origin/files/{path}
     */
    public HttpCallResult showOriginFile(URI originBaseUrl, String targetPath) {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) return HttpCallResult.clientError("path must not be blank");

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve("api/origin/files/" + cleanPath);

        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());

            String len = resp.headers().firstValue("Content-Length").orElse(null);
            String type = resp.headers().firstValue("Content-Type").orElse(null);
            String sha = resp.headers().firstValue("X-Content-SHA256").orElse(null);

            String json = String.format(
                    "{\"path\":\"%s\",\"size\":%s,\"contentType\":%s,\"sha256\":%s}",
                    JsonUtils.escapeJson(cleanPath),
                    len == null ? "null" : len,
                    type == null ? "null" : "\"" + JsonUtils.escapeJson(type) + "\"",
                    sha == null ? "null" : "\"" + JsonUtils.escapeJson(sha) + "\"");

            return HttpCallResult.http(resp.statusCode(), json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpCallResult.ioError("interrupted");
        } catch (IOException e) {
            return HttpCallResult.ioError(e.getMessage());
        }
    }

    /**
     * Lade eine Datei vom Origin-Server herunter und speichere sie lokal (Admin-API): GET /api/origin/files/{path}
     */
    public HttpCallResult downloadOriginFile(URI originBaseUrl, String targetPath, Path localTargetFile) {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");
        Objects.requireNonNull(localTargetFile, "localTargetFile");

        String cleanPath = PathUtils.stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) return HttpCallResult.clientError("path must not be blank");

        try {
            Path parent = localTargetFile.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException e) {
            return HttpCallResult.ioError("failed to create output directory: " + e.getMessage());
        }

        URI base = UriUtils.ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve("api/origin/files/" + cleanPath);

        HttpRequest req =
                HttpRequest.newBuilder(url).timeout(requestTimeout).GET().build();

        try {
            HttpResponse<Path> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(localTargetFile));
            return HttpCallResult.http(resp.statusCode(), String.valueOf(resp.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpCallResult.ioError("interrupted");
        } catch (IOException e) {
            return HttpCallResult.ioError(e.getMessage());
        }
    }
}
