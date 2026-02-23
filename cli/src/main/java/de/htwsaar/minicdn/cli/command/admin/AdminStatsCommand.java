package de.htwsaar.minicdn.cli.command.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.util.HttpUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Admin-Command zum Abruf von Router/Edge-Statistiken über die Admin-API.
 *
 * <p>Hinweis: Dieser Command selbst hat keine Default-Aktion und zeigt nur Usage an.
 * Für die eigentliche Ausführung {@code stats show} verwenden.</p>
 */
@Command(
        name = "stats",
        description = "Show Mini-CDN runtime statistics",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin stats show -H http://localhost:8080",
            "  admin stats show -H http://localhost:8080 --window-sec 120 --aggregate-edge=false",
            "  admin stats show -H http://localhost:8080 --json"
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
     * - 1: Exception/Netzwerkfehler</p>
     */
    @Command(
            name = "show",
            description = "Fetch and display structured stats from the router",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin stats show -H http://localhost:8080",
                "  admin stats show -H http://localhost:8080 --window-sec 10",
                "  admin stats show -H http://localhost:8080 --aggregate-edge=false",
                "  admin stats show -H http://localhost:8080 --json"
            })
    public static final class AdminStatsShowCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final CliContext ctx;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8082",
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

        @Option(
                names = {"--token"},
                defaultValue = "secret-token",
                paramLabel = "TOKEN",
                description = "Admin token")
        private String token;

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
                HttpRequest request = HttpUtils.newAdminRequestBuilder(url, token)
                        .timeout(ctx.defaultRequestTimeout())
                        .header("X-Admin-Token", token)
                        .GET()
                        .build();

                HttpResponse<String> response = ctx.httpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    err.printf("[ADMIN] Stats request failed: HTTP %d%n", response.statusCode());
                    if (response.body() != null && !response.body().isBlank()) {
                        err.println(response.body());
                    }
                    if (response.statusCode() == 401) {
                        err.println("[ADMIN] Hint: pass --admin-token <TOKEN> or set MINICDN_ADMIN_TOKEN / -Dminicdn.admin.token.");
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
                JsonNode downloads = root.path("downloads");

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

                printDownloadTotals(out, downloads.path("byFileTotal"));
                printDownloadByEdge(out, downloads.path("byFileByEdge"));

                out.flush();
                return 0;
            } catch (Exception ex) {
                err.println("[ADMIN] Stats request failed: " + ex.getMessage());
                err.flush();
                return 1;
            }
        }

        /**
         * Gibt die Gesamtzahl an Downloads je Datei formatiert aus.
         *
         * @param out Ziel-Output
         * @param byFileTotal JSON-Objekt Datei -> Download-Count
         */
        private static void printDownloadTotals(PrintWriter out, JsonNode byFileTotal) {
            out.println("  downloadsByFileTotal:");
            if (!byFileTotal.isObject() || byFileTotal.isEmpty()) {
                out.println("    (none)");
                return;
            }

            toSortedLongMap(byFileTotal).forEach((path, count) -> out.printf("    %s : %d%n", path, count));
        }

        /**
         * Gibt Downloadzahlen je Datei und Edge formatiert aus.
         *
         * @param out Ziel-Output
         * @param byFileByEdge JSON-Objekt Datei -> (Edge-URL -> Download-Count)
         */
        private static void printDownloadByEdge(PrintWriter out, JsonNode byFileByEdge) {
            out.println("  downloadsByFileByEdge:");
            if (!byFileByEdge.isObject() || byFileByEdge.isEmpty()) {
                out.println("    (none)");
                return;
            }

            Iterator<Map.Entry<String, JsonNode>> fileIterator = byFileByEdge.fields();
            Map<String, JsonNode> sortedByFile = new TreeMap<>();
            while (fileIterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = fileIterator.next();
                sortedByFile.put(entry.getKey(), entry.getValue());
            }

            sortedByFile.forEach((path, node) -> {
                out.printf("    %s%n", path);
                toSortedLongMap(node).forEach((edgeUrl, count) -> out.printf("      %s : %d%n", edgeUrl, count));
            });
        }

        /**
         * Extrahiert ein JSON-Objekt als sortierte Long-Map.
         *
         * @param node JSON-Objekt
         * @return sortierte Map mit numerischen Werten
         */
        private static Map<String, Long> toSortedLongMap(JsonNode node) {
            Map<String, Long> values = new TreeMap<>();
            if (!node.isObject()) {
                return values;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                values.put(entry.getKey(), Math.max(0L, entry.getValue().asLong(0L)));
            }
            return values;
        }
    }
}
