package de.htwsaar.minicdn.cli.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Admin-Statistiken.
 *
 * <p>Kennt keine konkrete Transportschicht.
 */
public class AdminStatsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    public AdminStatsService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Ruft die Statistiken vom Router ab.
     */
    public StatsResponse fetchStats(URI host, int windowSec, boolean aggregateEdge, String token) throws Exception {
        URI base = UriUtils.ensureTrailingSlash(host);
        URI url = base.resolve("api/cdn/admin/stats?windowSec=" + windowSec + "&aggregateEdge=" + aggregateEdge);

        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", resolveToken(token))));

        if (response.error() != null) {
            return new StatsResponse(0, response.error(), null);
        }

        int statusCode = Objects.requireNonNull(response.statusCode(), "statusCode");
        String rawBody = response.body();

        if (statusCode < 200 || statusCode >= 300) {
            return new StatsResponse(statusCode, rawBody, null);
        }

        JsonNode jsonData = MAPPER.readTree(rawBody);
        return new StatsResponse(statusCode, rawBody, jsonData);
    }

    private static String resolveToken(String token) {
        if (token != null && !token.isBlank()) {
            return token;
        }

        String envToken = System.getenv("MINICDN_ADMIN_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken;
        }

        String sysToken = System.getProperty("minicdn.admin.token");
        if (sysToken != null && !sysToken.isBlank()) {
            return sysToken;
        }

        return "secret-token";
    }

    /**
     * Hilfsklasse zum Kapseln von HTTP-Status, rohem Body und geparstem JSON.
     */
    public static class StatsResponse {
        private final int statusCode;
        private final String rawBody;
        private final JsonNode jsonData;

        public StatsResponse(int statusCode, String rawBody, JsonNode jsonData) {
            this.statusCode = statusCode;
            this.rawBody = rawBody;
            this.jsonData = jsonData;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getRawBody() {
            return rawBody;
        }

        public JsonNode getJsonData() {
            return jsonData;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
