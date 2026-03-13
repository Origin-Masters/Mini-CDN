package de.htwsaar.minicdn.router.adapter;

import de.htwsaar.minicdn.router.domain.OriginAdminGateway;
import de.htwsaar.minicdn.router.dto.AdminFileResult;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP-Client fuer die Origin-Admin-API.
 *
 * <p>Der Adapter baut die Ziel-URLs, setzt den Header {@code X-Admin-Token} und nutzt
 * ein festes Request-Timeout von 10 Sekunden. Laufzeitfehler werden nicht geworfen,
 * sondern als {@link AdminFileResult} mit Statuscode {@code 500} zurueckgegeben.</p>
 */
@Component
public class OriginHttpClient implements OriginAdminGateway {

    private final HttpClient httpClient;
    private final String adminToken;
    private final URI defaultOriginBaseUrl;
    private final Duration timeout;

    /**
     * Erstellt einen Client fuer Admin-Aufrufe gegen den Origin-Service.
     *
     * @param httpClient synchroner Java-HTTP-Client
     * @param adminToken Token fuer den Header {@code X-Admin-Token}
     * @param originBaseUrl Basis-URL des Origin-Service, z. B. {@code http://localhost:8080}
     */
    public OriginHttpClient(
            HttpClient httpClient,
            @Value("${minicdn.admin.token}") String adminToken,
            @Value("${origin.base-url:http://localhost:8080}") String originBaseUrl) {

        this.httpClient = httpClient;
        this.adminToken = adminToken;
        this.defaultOriginBaseUrl = URI.create(UrlUtil.ensureTrailingSlash(originBaseUrl));
        this.timeout = Duration.ofSeconds(10);
    }

    /**
     * Laedt eine Datei in den Origin hoch.
     *
     * @param path relativer Dateipfad unter {@code /api/origin/admin/files/}
     * @param body kompletter Dateiinhalt
     * @return bei 2xx ein Erfolgsergebnis, sonst ein Fehlerergebnis mit Response-Text
     */
    @Override
    public AdminFileResult uploadFile(String originBaseUrl, String path, byte[] body) {
        try {
            URI uploadUri = resolveBaseUrl(originBaseUrl).resolve("/api/origin/admin/files/" + path);

            HttpRequest request = HttpRequest.newBuilder(uploadUri)
                    .header("X-Admin-Token", adminToken)
                    .header("Content-Type", "application/octet-stream")
                    .timeout(timeout)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return AdminFileResult.success(response.statusCode(), null);
            }

            return AdminFileResult.error(response.statusCode(), "Origin upload failed: " + response.body());
        } catch (Exception e) {
            return AdminFileResult.error(500, "Origin upload failed: " + e.getMessage());
        }
    }

    /**
     * Loescht eine Datei im Origin.
     *
     * @param path relativer Dateipfad unter {@code /api/origin/admin/files/}
     * @return bei 2xx ein Erfolgsergebnis, sonst ein Fehlerergebnis mit Response-Text
     */
    @Override
    public AdminFileResult deleteFile(String originBaseUrl, String path) {
        try {
            URI deleteUri = resolveBaseUrl(originBaseUrl).resolve("/api/origin/admin/files/" + path);

            HttpRequest request = HttpRequest.newBuilder(deleteUri)
                    .header("X-Admin-Token", adminToken)
                    .timeout(timeout)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return AdminFileResult.success(response.statusCode(), null);
            }

            return AdminFileResult.error(response.statusCode(), "Origin delete failed: " + response.body());
        } catch (Exception e) {
            return AdminFileResult.error(500, "Origin delete failed: " + e.getMessage());
        }
    }

    /**
     * Liest eine Seite der Origin-Dateiliste.
     *
     * @param page Seitennummer (0-basiert)
     * @param size maximale Anzahl Eintraege pro Seite
     * @return Ergebnis mit HTTP-Statuscode und Response-Body der Listen-API
     */
    @Override
    public AdminFileResult listFiles(String originBaseUrl, int page, int size) {
        try {
            URI listUri = resolveBaseUrl(originBaseUrl)
                    .resolve(String.format("/api/origin/files?page=%d&size=%d", page, size));

            HttpRequest request = HttpRequest.newBuilder(listUri)
                    .header("X-Admin-Token", adminToken)
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return AdminFileResult.success(response.statusCode(), response.body());
        } catch (Exception e) {
            return AdminFileResult.error(500, "List failed: " + e.getMessage());
        }
    }

    @Override
    public AdminFileResult getFileMetadata(String originBaseUrl, String path) {
        try {
            URI headUri = resolveBaseUrl(originBaseUrl).resolve("api/origin/files/" + path);
            HttpRequest request = HttpRequest.newBuilder(headUri)
                    .header("X-Admin-Token", adminToken)
                    .timeout(timeout)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                var headers = response.headers();
                Map<String, Object> meta = Map.of(
                        "contentType", headers.firstValue("Content-Type").orElse("application/octet-stream"),
                        "lengthBytes", headers.firstValue("Content-Length").orElse("0"),
                        "sha256Hex", headers.firstValue("X-Content-SHA256").orElse(""));
                return AdminFileResult.success(status, meta);
            }
            return AdminFileResult.error(status, "Origin HEAD failed with status " + status);
        } catch (Exception e) {
            return AdminFileResult.error(500, "Origin HEAD failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isHealthy(String originBaseUrl, Duration timeout) {
        try {
            URI healthUri = resolveBaseUrl(originBaseUrl).resolve("api/origin/health");
            HttpRequest request = HttpRequest.newBuilder(healthUri)
                    .timeout(timeout == null ? Duration.ofSeconds(2) : timeout)
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            return status >= 200 && status < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    private URI resolveBaseUrl(String originBaseUrl) {
        if (originBaseUrl == null || originBaseUrl.isBlank()) {
            return defaultOriginBaseUrl;
        }
        return URI.create(UrlUtil.ensureTrailingSlash(originBaseUrl));
    }
}
