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

@Command(
        name = "edge",
        description = "Manage edge instances via router's admin API.",
        mixinStandardHelpOptions = true,
        descriptionHeading = "%nBeschreibung:%n",
        parameterListHeading = "%nParameter:%n",
        optionListHeading = "%nOptionen:%n",
        commandListHeading = "%nUnterbefehle:%n",
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  minicdn admin edge start -H http://localhost:8082 --region EU --port 8081 --origin http://localhost:8080 --wait-ready",
            "  minicdn admin edge managed -H http://localhost:8082",
            "  minicdn admin edge stop -H http://localhost:8082 edge-12345 --force"
        },
        subcommands = {
            AdminEdgeCommand.AdminEdgeStartCommand.class,
            AdminEdgeCommand.AdminEdgeStopCommand.class,
            AdminEdgeCommand.AdminEdgeManagedCommand.class,
            AdminEdgeCommand.AdminEdgeAutoStartCommand.class
        })
public final class AdminEdgeCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

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

    @Command(
            name = "start",
            description = "Start a managed edge process via router (POST /api/cdn/admin/edges/start).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin edge start -H http://localhost:8082 --region EU --port 8081 --origin http://localhost:8080 --auto-register=true --wait-ready",
                "  minicdn admin edge start --region US --port 8083 --origin http://localhost:8080 --auto-register=false"
            })
    public static final class AdminEdgeStartCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8082",
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}). Example: http://localhost:8082")
        private URI host;

        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Target region to register the edge under. Example: EU")
        private String region;

        @Option(
                names = "--port",
                required = true,
                paramLabel = "PORT",
                description = "TCP port for the edge HTTP server (1..65535). Example: 8081")
        private int port;

        @Option(
                names = "--origin",
                required = true,
                paramLabel = "ORIGIN_URL",
                description = "Origin base URL passed to the edge. Example: http://localhost:8080")
        private URI originBaseUrl;

        @Option(
                names = "--auto-register",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router registers the edge in its routing index (default: ${DEFAULT-VALUE}).")
        private boolean autoRegister;

        @Option(
                names = "--wait-ready",
                defaultValue = "false",
                paramLabel = "true|false",
                description =
                        "If true, router waits until the edge is ready before returning (default: ${DEFAULT-VALUE}).")
        private boolean waitReady;

        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
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
                HttpCallResult result =
                        parent.service().startEdge(host, region, port, originBaseUrl, autoRegister, waitReady);

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

                ConsoleUtils.info(
                        parent.ctx.out(),
                        "EDGE started instanceId=%s url=%s pid=%d region=%s",
                        instanceId,
                        url,
                        pid,
                        r);
                return 0;

            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE start failed: %s", ex.getMessage());
                return 1;
            }
        }
    }

    @Command(
            name = "stop",
            description = "Stop a managed edge process via router (DELETE /api/cdn/admin/edges/{instanceId}).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin edge stop -H http://localhost:8082 edge-12345 --force",
                "  minicdn admin edge stop -H http://localhost:8082 edge-12345 --force --deregister=false"
            })
    public static final class AdminEdgeStopCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8082",
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}). Example: http://localhost:8082")
        private URI host;

        @Parameters(
                index = "0",
                paramLabel = "INSTANCE_ID",
                description = "Managed instance id as returned by 'edge start'/'edge managed'. Example: edge-12345")
        private String instanceId;

        @Option(
                names = "--deregister",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router removes the edge from its routing index (default: ${DEFAULT-VALUE}).")
        private boolean deregister;

        @Option(
                names = "--force",
                defaultValue = "false",
                description = "Safety switch: required to actually stop the process (default: ${DEFAULT-VALUE}).")
        private boolean force;

        @Override
        public Integer call() {
            if (!force) {
                ConsoleUtils.error(
                        parent.ctx.err(), "EDGE stop is destructive. Re-run with --force. instanceId=%s", instanceId);
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
                    ConsoleUtils.info(
                            parent.ctx.out(),
                            "EDGE stopped instanceId=%s deregister=%s (HTTP %d)",
                            instanceId,
                            deregister,
                            sc);
                    return 0;
                }

                ConsoleUtils.error(
                        parent.ctx.err(),
                        "EDGE stop rejected: HTTP %d, body=%s",
                        sc,
                        Objects.toString(result.body(), ""));
                return 2;

            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE stop failed: %s", ex.getMessage());
                return 1;
            }
        }
    }

    @Command(
            name = "managed",
            description = "List all edges managed by the router (GET /api/cdn/admin/edges/managed).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin edge managed -H http://localhost:8082",
                "  minicdn admin edge managed -H http://localhost:8082 --json"
            })
    public static final class AdminEdgeManagedCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8082",
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}). Example: http://localhost:8082")
        private URI host;

        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
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

    @Command(
            name = "auto-start",
            description =
                    "Start multiple managed edge processes via router with auto port allocation (POST /api/cdn/admin/edges/start/auto).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin edge auto-start -H http://localhost:8082 --region EU --count 3 --origin http://localhost:8080 --auto-register=true --wait-ready",
                "  minicdn admin edge auto-start --region US --count 10 --origin http://localhost:8080 --auto-register=false",
                "  minicdn admin edge auto-start -H http://localhost:8082 --region EU --count 2 --origin http://localhost:8080 --json"
            })
    public static final class AdminEdgeAutoStartCommand implements Callable<Integer> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @ParentCommand
        private AdminEdgeCommand parent;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8082",
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}). Example: http://localhost:8082")
        private URI host;

        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Target region to register the edges under. Example: EU")
        private String region;

        @Option(
                names = "--count",
                required = true,
                paramLabel = "COUNT",
                description = "Number of edge processes to start (must be > 0). Example: 3")
        private int count;

        @Option(
                names = "--origin",
                required = true,
                paramLabel = "ORIGIN_URL",
                description = "Origin base URL passed to the edges. Example: http://localhost:8080")
        private URI originBaseUrl;

        @Option(
                names = "--auto-register",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router registers the edges in its routing index (default: ${DEFAULT-VALUE}).")
        private boolean autoRegister;

        @Option(
                names = "--wait-ready",
                defaultValue = "false",
                paramLabel = "true|false",
                description =
                        "If true, router waits until each edge is ready before returning (default: ${DEFAULT-VALUE}).")
        private boolean waitReady;

        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
        private boolean printJson;

        @Override
        public Integer call() {
            if (region == null || region.isBlank()) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE auto-start region must not be blank");
                return 3;
            }
            if (count <= 0) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE auto-start invalid --count: %d (expected > 0)", count);
                return 3;
            }
            if (originBaseUrl == null || originBaseUrl.getScheme() == null) {
                ConsoleUtils.error(
                        parent.ctx.err(), "EDGE auto-start invalid --origin (must be an absolute http/https URI)");
                return 3;
            }

            try {
                HttpCallResult result =
                        parent.service().startEdgesAuto(host, region, count, originBaseUrl, autoRegister, waitReady);

                if (result.error() != null) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE auto-start failed: %s", result.error());
                    return 1;
                }

                int sc = Objects.requireNonNull(result.statusCode(), "statusCode");
                String body = Objects.toString(result.body(), "");

                if (sc < 200 || sc >= 300) {
                    ConsoleUtils.error(parent.ctx.err(), "EDGE auto-start rejected: HTTP %d, body=%s", sc, body);
                    return 2;
                }

                if (printJson) {
                    parent.ctx.out().println(body);
                    parent.ctx.out().flush();
                    return 0;
                }

                JsonNode root = MAPPER.readTree(body);
                String r = root.path("region").asText("n/a");
                int requested = root.path("requested").asInt(-1);
                int started = root.path("started").asInt(-1);

                ConsoleUtils.info(
                        parent.ctx.out(),
                        "EDGE auto-start done region=%s requested=%d started=%d",
                        r,
                        requested,
                        started);

                JsonNode edges = root.path("edges");
                if (edges.isArray() && !edges.isEmpty()) {
                    parent.ctx.out().println("Started edges:");
                    for (JsonNode e : edges) {
                        String id = e.path("instanceId").asText("n/a");
                        String url = e.path("url").asText("n/a");
                        long pid = e.path("pid").asLong(-1);
                        String er = e.path("region").asText("n/a");
                        parent.ctx.out().printf("- %s region=%s url=%s pid=%d%n", id, er, url, pid);
                    }
                    parent.ctx.out().flush();
                }

                return 0;

            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "EDGE auto-start failed: %s", ex.getMessage());
                return 1;
            }
        }
    }
}
