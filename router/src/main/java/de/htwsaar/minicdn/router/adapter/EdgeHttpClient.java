package de.htwsaar.minicdn.router.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.common.logging.TraceIdFilter;
import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeNodeStats;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP-Adapter für die Kommunikation mit Edge-Knoten.
 *
 * <p>Enthält bewusst alle HTTP-Details wie URLs, Header und JSON-Parsing.</p>
 */
@Component
public class EdgeHttpClient implements EdgeGateway {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String adminToken;

    public EdgeHttpClient(
            HttpClient httpClient, ObjectMapper objectMapper, @Value("${minicdn.admin.token}") String adminToken) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.adminToken = adminToken;
    }

    /**
     * Prüft synchron, ob ein Edge-Knoten erreichbar ist.
     *
     * @param node Edge-Knoten
     * @param timeout Request-Timeout
     * @return true wenn der Health-Check HTTP 200 liefert
     */
    @Override
    public boolean isNodeResponsive(EdgeNode node, Duration timeout) {
        try {
            HttpRequest request = withCurrentTraceId(HttpRequest.newBuilder()
                            .uri(resolve(node, "api/edge/health"))
                            .timeout(timeout))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Prüft asynchron die Erreichbarkeit eines Edge-Knotens.
     *
     * @param node Edge-Knoten
     * @param timeout Request-Timeout
     * @return Future mit true bei HTTP 200
     */
    @Override
    public CompletableFuture<Boolean> checkNodeHealth(EdgeNode node, Duration timeout) {
        HttpRequest request = withCurrentTraceId(HttpRequest.newBuilder()
                        .uri(resolve(node, "api/edge/health"))
                        .timeout(timeout))
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> false);
    }

    /**
     * Lädt Admin-Statistiken von einem Edge-Knoten.
     *
     * @param node Edge-Knoten
     * @param windowSec Aggregationsfenster in Sekunden
     * @param timeout Request-Timeout
     * @return Statistikdaten
     * @throws Exception bei HTTP- oder Parsing-Fehlern
     */
    @Override
    public EdgeNodeStats fetchAdminStats(EdgeNode node, int windowSec, Duration timeout) throws Exception {
        HttpRequest request = withCurrentTraceId(HttpRequest.newBuilder()
                        .uri(resolve(node, "api/edge/admin/stats?windowSec=" + windowSec))
                        .timeout(timeout))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Edge stats request failed with HTTP " + response.statusCode());
        }

        EdgeStatsPayload payload = objectMapper.readValue(response.body(), EdgeStatsPayload.class);
        return new EdgeNodeStats(
                payload.cacheHits(),
                payload.cacheMisses(),
                payload.filesCached(),
                payload.downloadsByFile() == null ? Map.of() : payload.downloadsByFile());
    }

    /**
     * Sendet einen DELETE-Request an einen Edge-Knoten.
     *
     * @param node Edge-Knoten
     * @param endpoint Ziel-Endpoint relativ zur Edge-Base-URL
     * @return Future mit HTTP-Statuscode
     */
    @Override
    public CompletableFuture<Integer> sendDelete(EdgeNode node, String endpoint) {
        HttpRequest request = withCurrentTraceId(HttpRequest.newBuilder().uri(resolve(node, endpoint)))
                .DELETE()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::statusCode);
    }

    /**
     * Prüft die Readiness einer Edge-Instanz.
     *
     * @param baseUrl Basis-URL der Edge
     * @param timeout Request-Timeout
     * @return true bei HTTP 2xx
     */
    @Override
    public boolean isReady(URI baseUrl, Duration timeout) {
        URI readyUri = URI.create(UrlUtil.ensureTrailingSlash(baseUrl.toString()) + "api/edge/ready");

        try {
            HttpRequest request = withCurrentTraceId(
                            HttpRequest.newBuilder(readyUri).timeout(timeout))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Baut eine absolute URI auf Basis der Edge-URL.
     */
    private static URI resolve(EdgeNode node, String pathOrPathAndQuery) {
        URI base = URI.create(UrlUtil.ensureTrailingSlash(node.url()));
        return base.resolve(UrlUtil.stripLeadingSlash(pathOrPathAndQuery));
    }

    /**
     * Ergaenzt Admin-Token und Trace-Id falls vorhanden.
     */
    private HttpRequest.Builder withCurrentTraceId(HttpRequest.Builder builder) {
        builder.header("X-Admin-Token", adminToken);

        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId != null && !traceId.isBlank()) {
            builder.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }

        return builder;
    }

    /**
     * HTTP-Adapter-internes JSON-Modell für Edge-Statistiken.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EdgeStatsPayload(
            long cacheHits, long cacheMisses, long filesCached, Map<String, Long> downloadsByFile) {}
}
