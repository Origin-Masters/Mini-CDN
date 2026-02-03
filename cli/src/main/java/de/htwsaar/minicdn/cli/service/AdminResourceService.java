package de.htwsaar.minicdn.cli.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AdminResourceService {

    private final HttpClient httpClient;

    public AdminResourceService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Create a new resource on the CDN controller.
     * Returns 0 on success, 1 on error.
     */
    public int create(String serverUrl, String path, String origin, int cacheTtl) {
        try {
            String targetUrl = serverUrl.endsWith("/") ? serverUrl + "api/cdn/resources" : serverUrl + "/api/cdn/resources";
            // simple JSON payload; keep minimal to avoid extra deps
            String json = String.format("{\"path\":\"%s\",\"origin\":\"%s\",\"cacheTtl\":%d}",
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
