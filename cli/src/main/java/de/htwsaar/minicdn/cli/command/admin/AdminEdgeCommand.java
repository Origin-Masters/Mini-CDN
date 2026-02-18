package de.htwsaar.minicdn.cli.command.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminEdgeLauncherService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Admin-Commands zum Starten/Stoppen und Auflisten von managed Edge-Instanzen über den Router.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.</p>
 */
@Command(
        name = "edge",
        description = "Manage edge instances via router (start/stop/managed)",
        subcommands = {
                AdminEdgeCommand.AdminEdgeStartCommand.class,
                AdminEdgeCommand.AdminEdgeStopCommand.class,
                AdminEdgeCommand.AdminEdgeManagedCommand.class
        })
public final class AdminEdgeCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public AdminEdgeCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    private AdminEdgeLauncherService service() {
        return new AdminEdgeLauncherService(ctx.httpClient(), ctx.defaultRequestTimeout());
    }

    @Command(name = "start", description = "Start a managed edge instance via router", mixinStandardHelpOptions = true)
    public static final class AdminEdgeStartCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(names = {"-H", "--host"}, defaultValue = "http://localhost:8080",
                description = "Router base URL, e.g. http://localhost:8080")
        private URI host;

        @Option(names = "--region", required = true, description = "Target region, e.g. EU")
        private String region;

        @Option(names = "--port", required = true, description = "Edge HTTP port (1..65535)")
        private int port;

        @Option(names = "--origin", required = true, description = "Origin base URL, e.g. http://localhost:8080")
        private URI originBaseUrl;

        @Option(names = "--auto-register", defaultValue = "true",
                description = "Register edge in routing index after successful start (true/false)")
        private boolean autoRegister;

        @Option(names = "--wait-ready", defaultValue = "false",
                description = "Wait until the edge reports readiness (true/false)")
        private boolean waitReady;

        @Option(names = "--json", defaultValue = "false",
                description = "Print raw JSON response body")
        private boolean printJson;

        @Override
        public Integer call() {
            if (region == null || region.isBlank()) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE region must not be blank");
                return 3;
            }
            if (port <= 0 || port > 65535) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE invalid port: %d (expected 1..65535)", port);
                return 3;
            }
            if (originBaseUrl == null || originBaseUrl.getScheme() == null) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE invalid --origin (must be an absolute http/https URI)");
                return 3;
            }

            try {
                HttpCallResult result = parent.service()
                        .startEdge(host, region, port, originBaseUrl, autoRegister, waitReady);

                if (result.error() != null) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE start failed: %s", result.error());
                    return 1;
                }

                int sc = Objects.requireNonNull(result.statusCode(), "statusCode");
                String body = Objects.toString(result.body(), "");

                if (sc < 200 || sc >= 300) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE start rejected: HTTP %d, body=%s", sc, body);
                    return 2;
                }

                if (printJson) {
                    parent.ctx.out().println(body);
                    parent.ctx.out().flush();
                    return 0;
                }

                JsonNode n = MAPPER.readTree(body);
                String instanceId = n.path("instanceId").asText("n/a");
                String url = n.path("url").asText("n/a");
                long pid = n.path("pid").asLong(-1);
                String r = n.path("region").asText("n/a");

                ConsoleUtils.info(parent.ctx.out(),
                        "EDGE started instanceId=%s url=%s pid=%d region=%s",
                        instanceId, url, pid, r);
                return 0;

            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE start failed: %s", ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "stop", description = "Stop a managed edge instance via router", mixinStandardHelpOptions = true)
    public static final class AdminEdgeStopCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(names = {"-H", "--host"}, defaultValue = "http://localhost:8080",
                description = "Router base URL, e.g. http://localhost:8080")
        private URI host;

        @Parameters(index = "0", paramLabel = "instanceId", description = "Managed instanceId, e.g. edge-12345")
        private String instanceId;

        @Option(names = "--deregister", defaultValue = "true",
                description = "Deregister edge from routing index (true/false)")
        private boolean deregister;

        @Option(names = "--force", defaultValue = "false",
                description = "Actually perform the stop (safety switch)")
        private boolean force;

        @Override
        public Integer call() {
            if (!force) {
                ConsoleUtils.error(parent.ctx.err(),
                        "EDGE stop is destructive. Re-run with --force. instanceId=%s", instanceId);
                return 3;
            }

            try {
                HttpCallResult result = parent.service().stopEdge(host, instanceId, deregister);

                if (result.error() != null) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE stop failed: %s", result.error());
                    return 1;
                }

                int sc = Objects.requireNonNull(result.statusCode(), "statusCode");
                if (sc >= 200 && sc < 300) {
                    ConsoleUtils.info(parent.ctx.out(),
                            "EDGE stopped instanceId=%s deregister=%s (HTTP %d)",
                            instanceId, deregister, sc);
                    return 0;
                }

                ConsoleUtils.error(parent.ctx.err(),
                        "EDGE stop rejected: HTTP %d, body=%s",
                        sc, Objects.toString(result.body(), ""));
                return 2;

            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE stop failed: %s", ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "managed", description = "List managed edges via router", mixinStandardHelpOptions = true)
    public static final class AdminEdgeManagedCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(names = {"-H", "--host"}, defaultValue = "http://localhost:8080",
                description = "Router base URL, e.g. http://localhost:8080")
        private URI host;

        @Option(names = "--json", defaultValue = "false",
                description = "Print raw JSON response body")
        private boolean printJson;

        @Override
        public Integer call() {
            try {
                HttpCallResult result = parent.service().listManaged(host);

                if (result.error() != null) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE managed failed: %s", result.error());
                    return 1;
                }

                int sc = Objects.requireNonNull(result.statusCode(), "statusCode");
                String body = Objects.toString(result.body(), "");

                if (sc < 200 || sc >= 300) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE managed rejected: HTTP %d, body=%s", sc, body);
                    return 2;
                }

                if (printJson) {
                    parent.ctx.out().println(body);
                    parent.ctx.out().flush();
                    return 0;
                }

                JsonNode arr = MAPPER.readTree(body);
                if (!arr.isArray() || arr.isEmpty()) {
                    ConsoleUtils.info(parent.ctx.out(), "EDGE no managed instances");
                    return 0;
                }

                parent.ctx.out().println("Managed edges:");
                for (JsonNode e : arr) {
                    String id = e.path("instanceId").asText("n/a");
                    String region = e.path("region").asText("n/a");
                    String url = e.path("url").asText("n/a");
                    long pid = e.path("pid").asLong(-1);
                    parent.ctx.out().printf("- %s region=%s url=%s pid=%d%n", id, region, url, pid);
                }
                parent.ctx.out().flush();
                return 0;

            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE managed failed: %s", ex.getMessage());
                return 1;
            }
        }
    }
}
