package de.htwsaar.minicdn.cli.command.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.admin.AdminStatsService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.StatsFormatter;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Admin-Command zum Abruf von Router/Edge-Statistiken über die Admin-API.
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

    public AdminStatsCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

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
        private final AdminStatsService adminStatsService;

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
            this.adminStatsService = new AdminStatsService(ctx.transportClient(), ctx.defaultRequestTimeout());
        }

        @Override
        public Integer call() {
            PrintWriter out = ctx.out();
            PrintWriter err = ctx.err();

            URI effectiveHost = Objects.requireNonNull(host, "host");
            int safeWindow = Math.max(1, windowSec);

            try {
                AdminStatsService.StatsResponse response =
                        adminStatsService.fetchStats(effectiveHost, safeWindow, aggregateEdge, token);

                if (!response.isSuccess()) {
                    ConsoleUtils.error(err, "[ADMIN] Stats request failed: HTTP %d", response.getStatusCode());

                    if (response.getRawBody() != null && !response.getRawBody().isBlank()) {
                        ConsoleUtils.error(err, response.getRawBody());
                    }

                    if (response.getStatusCode() == 401) {
                        ConsoleUtils.error(
                                err,
                                "[ADMIN] Hint: pass --admin-token <TOKEN> or set MINICDN_ADMIN_TOKEN / -Dminicdn.admin.token.");
                    }
                    return 2;
                }

                JsonNode root = response.getJsonData();

                if (printJson) {
                    out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                    out.flush();
                    return 0;
                }

                formatAndPrintStats(out, root, safeWindow);
                out.flush();
                return 0;

            } catch (Exception ex) {
                ConsoleUtils.error(err, "[ADMIN] Stats request failed: %s", ex.getMessage());
                return 1;
            }
        }

        private void formatAndPrintStats(PrintWriter out, JsonNode root, int safeWindow) {
            JsonNode router = root.path("router");
            JsonNode cache = root.path("cache");
            JsonNode nodes = root.path("nodes");
            JsonNode downloads = root.path("downloads");

            out.println("[ADMIN] Mini-CDN Stats");
            out.printf("  timestamp         : %s%n", root.path("timestamp").asText("n/a"));
            out.printf("  windowSec         : %d%n", root.path("windowSec").asInt(safeWindow));
            out.printf(
                    "  totalRequests     : %d%n", router.path("totalRequests").asLong());
            out.printf(
                    "  requestsPerMinute : %d%n",
                    router.path("requestsPerMinute").asLong());
            out.printf(
                    "  activeClients     : %d%n", router.path("activeClients").asLong());
            out.printf(
                    "  routingErrors     : %d%n", router.path("routingErrors").asLong());
            out.printf("  cacheHits         : %d%n", cache.path("hits").asLong());
            out.printf("  cacheMisses       : %d%n", cache.path("misses").asLong());
            out.printf("  cacheHitRatio     : %.4f%n", cache.path("hitRatio").asDouble());
            out.printf("  filesLoaded       : %d%n", cache.path("filesLoaded").asLong());
            out.printf("  nodesTotal        : %d%n", nodes.path("total").asLong());

            StatsFormatter.printDownloadTotals(out, downloads.path("byFileTotal"));
            StatsFormatter.printDownloadByEdge(out, downloads.path("byFileByEdge"));
        }
    }
}
