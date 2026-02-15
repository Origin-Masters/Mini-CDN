package de.htwsaar.minicdn.cli.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    public HttpCallResult create(URI cdnBaseUrl, String path, String origin, int cacheTtl) {
        Objects.requireNonNull(cdnBaseUrl, "cdnBaseUrl");

        URI base = ensureTrailingSlash(cdnBaseUrl);
        URI target = base.resolve("api/cdn/resources");

        String json = String.format(
                "{\"path\":\"%s\",\"origin\":\"%s\",\"cacheTtl\":%d}", escapeJson(path), escapeJson(origin), cacheTtl);

        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return sendForStringBody(request);
    }

    /**
     * Upload a local file to Origin admin API: PUT /api/origin/admin/files/{path}
     */
    public HttpCallResult uploadToOrigin(URI originBaseUrl, String targetPath, Path localFile)
            throws FileNotFoundException {
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");
        Objects.requireNonNull(localFile, "localFile");

        String cleanPath = stripLeadingSlash(Objects.toString(targetPath, ""));
        if (cleanPath.isBlank()) {
            return HttpCallResult.clientError("targetPath must not be blank");
        }

        URI base = ensureTrailingSlash(originBaseUrl);
        URI url = base.resolve("api/origin/admin/files/" + cleanPath);

        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(localFile))
                .build();

        return sendForStringBody(req);
    }

    private HttpCallResult sendForStringBody(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return HttpCallResult.http(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpCallResult.ioError("interrupted");
        } catch (IOException e) {
            return HttpCallResult.ioError(e.getMessage());
        }
    }

    private static URI ensureTrailingSlash(URI uri) {
        String s = uri.toString();
        return URI.create(s.endsWith("/") ? s : s + "/");
    }

    private static String stripLeadingSlash(String p) {
        if (p == null || p.isBlank()) return "";
        return p.startsWith("/") ? p.substring(1) : p;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public record HttpCallResult(Integer statusCode, String body, String error) {
        public static HttpCallResult http(int statusCode, String body) {
            return new HttpCallResult(statusCode, body, null);
        }

        public static HttpCallResult ioError(String message) {
            return new HttpCallResult(null, null, message == null ? "io error" : message);
        }

        public static HttpCallResult clientError(String message) {
            return new HttpCallResult(400, null, message);
        }

        public boolean is2xx() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }
    }
}
