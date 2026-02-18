package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.util.HttpUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
     * Create a new CDN resource via admin API: POST /api/cdn/resources
     */
    public HttpCallResult create(URI cdnBaseUrl, String path, String origin, int cacheTtl) {
        Objects.requireNonNull(cdnBaseUrl, "cdnBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(cdnBaseUrl);
        URI target = base.resolve("api/cdn/resources");

        String json = String.format(
                "{\"path\":\"%s\",\"origin\":\"%s\",\"cacheTtl\":%d}",
                JsonUtils.escapeJson(path), JsonUtils.escapeJson(origin), cacheTtl);

        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return HttpUtils.sendForStringBody(httpClient, request);
    }

    /**
     * Upload a local file to Origin admin API: PUT /api/origin/admin/files/{path}
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
}
