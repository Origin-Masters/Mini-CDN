package de.htwsaar.minicdn.cli.adminCommands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI-Befehl zum Abruf von Router/Edge-Statistiken über die Admin-API.
 */
@Command(
        name = "stats",
        description = "Show Mini-CDN runtime statistics",
        subcommands = {AdminStatsCommand.AdminStatsShowCommand.class})
public class AdminStatsCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /**
     * Ruft den Endpoint /api/cdn/admin/stats auf und gibt die Daten aus.
     */
    @Command(name = "show", description = "Fetch and display structured stats from the router")
    public static class AdminStatsShowCommand implements Runnable {

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8080",
                description = "Basis-URL des Routers")
        String host;

        @Option(
                names = "--window-sec",
                defaultValue = "60",
                description = "Zeitfenster in Sekunden für exakte Requests/Minute")
        int windowSec;

        @Option(
                names = "--aggregate-edge",
                defaultValue = "true",
                description = "Edge-Metriken aggregieren (true/false)")
        boolean aggregateEdge;

        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Vollständige JSON-Antwort ausgeben")
        boolean printJson;

        @Override
        public void run() {
            String baseHost = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
            int safeWindow = Math.max(1, windowSec);
            String url = baseHost + "/api/cdn/admin/stats?windowSec=" + safeWindow + "&aggregateEdge=" + aggregateEdge;

            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.printf("[ADMIN] Stats request failed: HTTP %d%n", response.statusCode());
                    System.err.println(response.body());
                    return;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());

                if (printJson) {
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                    return;
                }

                JsonNode router = root.path("router");
                JsonNode cache = root.path("cache");
                JsonNode nodes = root.path("nodes");

                System.out.println("[ADMIN] Mini-CDN Stats");
                System.out.printf("  timestamp        : %s%n", root.path("timestamp").asText("n/a"));
                System.out.printf("  windowSec        : %d%n", root.path("windowSec").asInt());
                System.out.printf("  totalRequests    : %d%n", router.path("totalRequests").asLong());
                System.out.printf("  requestsPerMinute: %d%n", router.path("requestsPerMinute").asLong());
                System.out.printf("  activeClients    : %d%n", router.path("activeClients").asLong());
                System.out.printf("  routingErrors    : %d%n", router.path("routingErrors").asLong());
                System.out.printf("  cacheHits        : %d%n", cache.path("hits").asLong());
                System.out.printf("  cacheMisses      : %d%n", cache.path("misses").asLong());
                System.out.printf("  cacheHitRatio    : %.4f%n", cache.path("hitRatio").asDouble());
                System.out.printf("  filesLoaded      : %d%n", cache.path("filesLoaded").asLong());
                System.out.printf("  nodesTotal       : %d%n", nodes.path("total").asLong());
            } catch (Exception ex) {
                System.err.println("[ADMIN] Stats request failed: " + ex.getMessage());
            }
        }
    }
}