package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

/**
 * Kapselt HTTP-Zugriffe auf Edge-Nodes (Health, Admin-Stats, Cache-Invalidierung).
 *
 * <p>Keine neue Logik: Es werden dieselben Requests gebaut und ausgewertet,
 * aber Controller-Code wird entlastet.
 */
@Service
public class EdgeHttpClient {

    private final HttpClient httpClient;

    public EdgeHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Hilfsmethode zur Überprüfung der Erreichbarkeit (Acknowledgement Handling).
     *
     * @param node Edge-Knoten
     * @param timeout Timeout für den Health-Request
     * @return {@code true}, wenn HTTP 200 zurückgegeben wurde
     */
    public boolean isNodeResponsive(EdgeNode node, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(resolve(node, "api/edge/health"))
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Führt asynchron einen Health-Check gegen den angegebenen Edge-Knoten aus.
     *
     * @param node zu prüfender Knoten
     * @param timeout Timeout für den Health-Request
     * @return Future mit {@code true}, wenn HTTP 200 zurückgegeben wurde
     */
    public CompletableFuture<Boolean> checkNodeHealth(EdgeNode node, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolve(node, "api/edge/health"))
                .timeout(timeout)
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> false);
    }

    /**
     * Ruft Edge-Admin-Stats synchron ab.
     *
     * @param node Edge-Knoten
     * @param windowSec Fenster in Sekunden
     * @param timeout Request-Timeout
     * @return HTTP Response als String-Body
     */
    public HttpResponse<String> fetchEdgeAdminStats(EdgeNode node, int windowSec, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolve(node, "api/edge/admin/stats?windowSec=" + windowSec))
                .timeout(timeout)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sendet ein DELETE an einen Edge-Endpoint (asynchron).
     *
     * @param node Edge-Knoten
     * @param endpoint Pfad inkl. Query (z. B. {@code /api/edge/cache/all})
     * @return Future der HTTP Response
     */
    public CompletableFuture<HttpResponse<String>> sendDelete(EdgeNode node, String endpoint) {
        HttpRequest request =
                HttpRequest.newBuilder().uri(resolve(node, endpoint)).DELETE().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Baut aus der Node-Base-URL und einem relativen Pfad (inkl. optionaler Query) eine robuste Ziel-URI.
     * <p>
     * Diese Methode verhindert klassische Slash-Fehler wie {@code http://host:8081api/...}
     * und ist die einzige Stelle, an der URL-Zusammensetzung passieren darf (DRY).
     * </p>
     *
     * @param node registrierter Edge-Knoten mit Base-URL
     * @param pathOrPathAndQuery relativer Pfad, z. B. {@code api/edge/health} oder {@code api/edge/admin/stats?windowSec=60}
     * @return aufgelöste Ziel-URI
     */
    private static URI resolve(EdgeNode node, String pathOrPathAndQuery) {
        URI base = URI.create(UrlUtil.ensureTrailingSlash(node.url()));
        return base.resolve(UrlUtil.stripLeadingSlash(pathOrPathAndQuery));
    }
}
