package de.htwsaar.minicdn.cli.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class AdminResourceService {

    private final HttpClient httpClient;

    public AdminResourceService() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public int create(String serverUrl, String path, String origin, int cacheTtl) {
        try {
            String targetUrl =
                    serverUrl.endsWith("/") ? serverUrl + "api/cdn/resources" : serverUrl + "/api/cdn/resources";
            // simple JSON payload; keep minimal to avoid extra deps
            String json = String.format(
                    "{\"path\":\"%s\",\"origin\":\"%s\",\"cacheTtl\":%d}",
                    escapeJson(path), escapeJson(origin), cacheTtl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                System.out.println("Resource created successfully:");
                System.out.println(response.body());
                return 0;
            } else {
                System.err.println("Failed to create resource: HTTP " + response.statusCode());
                System.err.println("Details: " + response.body());
                return 1;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error creating resource: " + e.getMessage());
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    /**
     * Upload a local file to Origin admin API. Returns 0 on success, 1 on error.
     */
    public int uploadToOrigin(String originBaseUrl, String targetPath, Path localFile) {
        try {
            byte[] body = Files.readAllBytes(localFile);

            String cleanPath = targetPath.startsWith("/") ? targetPath.substring(1) : targetPath;
            String url = originBaseUrl.endsWith("/")
                    ? originBaseUrl + "api/origin/admin/files/" + cleanPath
                    : originBaseUrl + "/api/origin/admin/files/" + cleanPath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());

            // OriginController: 新建 201，覆盖 204 [file:1]
            if (resp.statusCode() == HttpURLConnection.HTTP_CREATED
                    || resp.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                System.out.println("Upload OK: " + url);
                return 0;
            } else {
                System.err.println("Upload failed: HTTP " + resp.statusCode());
                return 1;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Upload error: " + e.getMessage());
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
