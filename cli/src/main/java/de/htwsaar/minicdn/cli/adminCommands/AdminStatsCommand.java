package de.htwsaar.minicdn.cli.adminCommands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Admin-Command zum Abruf von Router/Edge-Statistiken über die Admin-API.
 *
 * <p>Hinweis: Dieser Command selbst hat keine Default-Aktion und zeigt nur Usage an.
 * Für die eigentliche Ausführung {@code stats show} verwenden.
 */
@Command(
        name = "stats",
        description = "Show Mini-CDN runtime statistics",
        mixinStandardHelpOptions = true,
        subcommands = {AdminStatsCommand.AdminStatsShowCommand.class})
public class AdminStatsCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /**
     * Ruft {@code GET /api/cdn/admin/stats} auf und gibt die Daten formatiert aus.
     *
     * <p>Exit-Codes:
     * - 0: OK
     * - 2: HTTP-Fehlerstatus (non-2xx)
     * - 1: Exception/Netzwerkfehler
     */
    @Command(name = "show", description = "Fetch and display structured stats from the router")
    public static final class AdminStatsShowCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final CliContext ctx;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8080",
                description = "Basis-URL des Routers, z.B. http://localhost:8080")
        URI host;

        @Option(
                names = "--window-sec",
                defaultValue = "60",
                description = "Zeitfenster in Sekunden für exakte Requests/Minute (min. 1)")
        int windowSec;

        @Option(
                names = "--aggregate-edge",
                defaultValue = "true",
                description = "Edge-Metriken aggregieren (true/false)")
        boolean aggregateEdge;

        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Vollständige JSON-Antwort pretty-printed ausgeben")
        boolean printJson;

        public AdminStatsShowCommand(CliContext ctx) {
            this.ctx = Objects.requireNonNull(ctx, "ctx");
        }

        @Override
        public Integer call() {
            PrintWriter out = ctx.out();
            PrintWriter err = ctx.err();

            int safeWindow = Math.max(1, windowSec);

            URI base = ensureTrailingSlash(host);
            URI url = base.resolve("api/cdn/admin/stats?windowSec=" + safeWindow + "&aggregateEdge=" + aggregateEdge);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(url)
                        .timeout(ctx.defaultRequestTimeout())
                        .GET()
                        .build();

                HttpResponse<String> response = ctx.httpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    err.printf("[ADMIN] Stats request failed: HTTP %d%n", response.statusCode());
                    if (response.body() != null && !response.body().isBlank()) {
                        err.println(response.body());
                    }
                    err.flush();
                    return 2;
                }

                JsonNode root = MAPPER.readTree(response.body());

                if (printJson) {
                    out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                    out.flush();
                    return 0;
                }

                JsonNode router = root.path("router");
                JsonNode cache = root.path("cache");
                JsonNode nodes = root.path("nodes");

                out.println("[ADMIN] Mini-CDN Stats");
                out.printf("  timestamp         : %s%n", root.path("timestamp").asText("n/a"));
                out.printf("  windowSec         : %d%n", root.path("windowSec").asInt(safeWindow));
                out.printf("  totalRequests     : %d%n", router.path("totalRequests").asLong());
                out.printf("  requestsPerMinute : %d%n", router.path("requestsPerMinute").asLong());
                out.printf("  activeClients     : %d%n", router.path("activeClients").asLong());
                out.printf("  routingErrors     : %d%n", router.path("routingErrors").asLong());
                out.printf("  cacheHits         : %d%n", cache.path("hits").asLong());
                out.printf("  cacheMisses       : %d%n", cache.path("misses").asLong());
                out.printf("  cacheHitRatio     : %.4f%n", cache.path("hitRatio").asDouble());
                out.printf("  filesLoaded       : %d%n", cache.path("filesLoaded").asLong());
                out.printf("  nodesTotal        : %d%n", nodes.path("total").asLong());
                out.flush();

                return 0;
            } catch (Exception ex) {
                err.println("[ADMIN] Stats request failed: " + ex.getMessage());
                err.flush();
                return 1;
            }
        }

        private static URI ensureTrailingSlash(URI uri) {
            String s = uri.toString();
            return URI.create(s.endsWith("/") ? s : s + "/");
        }
    }
}
