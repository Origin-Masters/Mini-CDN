package de.htwsaar.minicdn.cli.command.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

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
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  minicdn admin stats show -H http://localhost:8080",
            "  minicdn admin stats show -H http://localhost:8080 --window-sec 120 --aggregate-edge=false",
            "  minicdn admin stats show -H http://localhost:8080 --json"
        },
        subcommands = {AdminStatsCommand.AdminStatsShowCommand.class})
public final class AdminStatsCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public AdminStatsCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Ruft {@code GET /api/cdn/admin/stats} auf und gibt die Daten formatiert aus.
     *
     * <p>Exit-Codes:
     * - 0: OK
     * - 2: HTTP-Fehlerstatus (non-2xx)
     * - 1: Exception/Netzwerkfehler
     */
    @Command(
            name = "show",
            description = "Fetch and display structured stats from the router",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin stats show -H http://localhost:8080",
                "  minicdn admin stats show -H http://localhost:8080 --window-sec 10",
                "  minicdn admin stats show -H http://localhost:8080 --aggregate-edge=false",
                "  minicdn admin stats show -H http://localhost:8080 --json"
            })
    public static final class AdminStatsShowCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final CliContext ctx;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8080",
                paramLabel = "ROUTER_URL",
                description = "Basis-URL des Routers, z.B. http://localhost:8080")
        private URI host;

        @Option(
                names = "--window-sec",
                defaultValue = "60",
                paramLabel = "SECONDS",
                description = "Zeitfenster in Sekunden für exakte Requests/Minute (min. 1)")
        private int windowSec;

        @Option(
                names = "--aggregate-edge",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "Edge-Metriken aggregieren (true/false)")
        private boolean aggregateEdge;

        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Vollständige JSON-Antwort pretty-printed ausgeben")
        private boolean printJson;

        public AdminStatsShowCommand(CliContext ctx) {
            this.ctx = Objects.requireNonNull(ctx, "ctx");
        }

        @Override
        public Integer call() {
            PrintWriter out = ctx.out();
            PrintWriter err = ctx.err();

            URI effectiveHost = Objects.requireNonNull(host, "host");
            int safeWindow = Math.max(1, windowSec);

            URI base = UriUtils.ensureTrailingSlash(effectiveHost);
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
                out.printf(
                        "  totalRequests     : %d%n",
                        router.path("totalRequests").asLong());
                out.printf(
                        "  requestsPerMinute : %d%n",
                        router.path("requestsPerMinute").asLong());
                out.printf(
                        "  activeClients     : %d%n",
                        router.path("activeClients").asLong());
                out.printf(
                        "  routingErrors     : %d%n",
                        router.path("routingErrors").asLong());
                out.printf("  cacheHits         : %d%n", cache.path("hits").asLong());
                out.printf("  cacheMisses       : %d%n", cache.path("misses").asLong());
                out.printf(
                        "  cacheHitRatio     : %.4f%n", cache.path("hitRatio").asDouble());
                out.printf(
                        "  filesLoaded       : %d%n", cache.path("filesLoaded").asLong());
                out.printf("  nodesTotal        : %d%n", nodes.path("total").asLong());
                out.flush();

                return 0;
            } catch (Exception ex) {
                err.println("[ADMIN] Stats request failed: " + ex.getMessage());
                err.flush();
                return 1;
            }
        }
    }
}
