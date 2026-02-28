package de.htwsaar.minicdn.cli.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.util.HttpUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AdminStatsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CliContext ctx;

    public AdminStatsService(CliContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Ruft die Statistiken vom Router ab.
     */
    public StatsResponse fetchStats(URI host, int windowSec, boolean aggregateEdge, String token) throws Exception {
        URI base = UriUtils.ensureTrailingSlash(host);
        URI url = base.resolve("api/cdn/admin/stats?windowSec=" + windowSec + "&aggregateEdge=" + aggregateEdge);

        HttpRequest request = HttpUtils.newAdminRequestBuilder(url, token)
                .timeout(ctx.defaultRequestTimeout())
                .header("X-Admin-Token", token)
                .GET()
                .build();

        HttpResponse<String> response = ctx.httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new StatsResponse(response.statusCode(), response.body(), null);
        }

        JsonNode jsonData = MAPPER.readTree(response.body());
        return new StatsResponse(response.statusCode(), response.body(), jsonData);
    }

    /**
     * Hilfsklasse zum Kapseln von HTTP-Status, rohem Body und geparstem JSON, damit die Command-Logik schlanker bleibt.
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
