package de.htwsaar.minicdn.cli.application.admin;

import com.fasterxml.jackson.databind.JsonNode;
import de.htwsaar.minicdn.cli.adapter.in.cli.support.JsonUtils;
import de.htwsaar.minicdn.cli.adapter.in.cli.support.StatsFormatter;
import de.htwsaar.minicdn.cli.domain.model.StatsResponse;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für Admin-Statistiken.
 *
 * <p>Der Abruf selbst wird über einen fachlichen Port gekapselt. Dieser Service
 * enthält nur noch Validierung sowie Darstellung erfolgreicher Antworten.</p>
 */
public final class AdminStatsService {

    private final AdminOperations adminOperations;

    /**
     * Erzeugt einen neuen Service für Admin-Statistiken.
     *
     * @param adminOperations fachlicher Port für administrative Remote-Aufrufe
     */
    public AdminStatsService(AdminOperations adminOperations) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
    }

    /**
     * Ruft die Statistiken vom Router ab.
     *
     * @param host Basis-URL des Routers
     * @param windowSec Zeitfenster in Sekunden
     * @param aggregateEdge Kennzeichen, ob Edge-Metriken aggregiert werden sollen
     * @param token Admin-Token; optional, falls alternativ per Environment oder System-Property gesetzt
     * @return normierte Antwort inklusive Status, Roh-Body, JSON-Baum und technischem Fehler
     */
    public StatsResponse fetchStats(URI host, int windowSec, boolean aggregateEdge, String token) {
        Objects.requireNonNull(host, "host");
        if (windowSec < 1) {
            return StatsResponse.clientError("windowSec must be >= 1");
        }

        final String effectiveToken;
        try {
            effectiveToken = resolveToken(token);
        } catch (IllegalArgumentException ex) {
            return StatsResponse.clientError(ex.getMessage());
        }

        return adminOperations.fetchStats(host, windowSec, aggregateEdge, effectiveToken);
    }

    /**
     * Formatiert eine erfolgreiche Antwort als schön eingerücktes JSON.
     *
     * @param response erfolgreiche Statistikantwort
     * @return pretty-printed JSON
     */
    public String formatPrettyJson(StatsResponse response) {
        StatsResponse successfulResponse = requireSuccessfulResponse(response);
        return JsonUtils.formatJson(successfulResponse.rawBody());
    }

    /**
     * Formatiert eine erfolgreiche Antwort menschenlesbar für die CLI.
     *
     * @param response erfolgreiche Statistikantwort
     * @param defaultWindowSec Fallback-Zeitfenster für die Ausgabe
     * @return formatierte Textausgabe
     */
    public String formatHumanReadable(StatsResponse response, int defaultWindowSec) {
        StatsResponse successfulResponse = requireSuccessfulResponse(response);
        JsonNode root = Objects.requireNonNull(successfulResponse.jsonData(), "jsonData");

        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);

        JsonNode router = root.path("router");
        JsonNode cache = root.path("cache");
        JsonNode nodes = root.path("nodes");
        JsonNode downloads = root.path("downloads");

        out.println("[ADMIN] Mini-CDN Stats");
        out.printf("  timestamp         : %s%n", root.path("timestamp").asText("n/a"));
        out.printf("  windowSec         : %d%n", root.path("windowSec").asInt(defaultWindowSec));
        out.printf("  totalRequests     : %d%n", router.path("totalRequests").asLong());
        out.printf(
                "  requestsPerMinute : %d%n", router.path("requestsPerMinute").asLong());
        out.printf("  activeClients     : %d%n", router.path("activeClients").asLong());
        out.printf("  routingErrors     : %d%n", router.path("routingErrors").asLong());
        out.printf("  cacheHits         : %d%n", cache.path("hits").asLong());
        out.printf("  cacheMisses       : %d%n", cache.path("misses").asLong());
        out.printf("  cacheHitRatio     : %.4f%n", cache.path("hitRatio").asDouble());
        out.printf("  filesLoaded       : %d%n", cache.path("filesLoaded").asLong());
        out.printf("  nodesTotal        : %d%n", nodes.path("total").asLong());

        StatsFormatter.printDownloadTotals(out, downloads.path("byFileTotal"));
        StatsFormatter.printDownloadByEdge(out, downloads.path("byFileByEdge"));

        out.flush();
        return buffer.toString();
    }

    private static StatsResponse requireSuccessfulResponse(StatsResponse response) {
        Objects.requireNonNull(response, "response");
        if (!response.isSuccess()) {
            throw new IllegalArgumentException("response must be successful before formatting");
        }
        return response;
    }

    private static String resolveToken(String token) {
        String directToken = Objects.toString(token, "").trim();
        if (!directToken.isBlank()) {
            return directToken;
        }

        String envToken = System.getenv("MINICDN_ADMIN_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }

        String systemToken = System.getProperty("minicdn.admin.token");
        if (systemToken != null && !systemToken.isBlank()) {
            return systemToken.trim();
        }

        throw new IllegalArgumentException("admin token must not be blank");
    }
}
