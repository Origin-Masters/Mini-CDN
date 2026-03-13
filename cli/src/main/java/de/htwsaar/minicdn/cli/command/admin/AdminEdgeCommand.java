package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ORIGIN_URL;
import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import com.fasterxml.jackson.databind.JsonNode;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminEdgeService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.common.serialization.JacksonCodec;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Stellt Admin-Befehle zur Verwaltung von Edge-Instanzen über die Router-Admin-API bereit.
 */
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
            "  admin edge start --region EU --port 8081 --origin http://localhost:8080 --wait-ready",
            "  admin edge managed",
            "  admin edge stop edge-12345 --force",
            "  admin edge stop-region --region EU --force"
        },
        subcommands = {
            AdminEdgeCommand.AdminEdgeStartCommand.class,
            AdminEdgeCommand.AdminEdgeStopCommand.class,
            AdminEdgeCommand.AdminEdgeStopRegionCommand.class,
            AdminEdgeCommand.AdminEdgeManagedCommand.class,
            AdminEdgeCommand.AdminEdgeAutoStartCommand.class
        })
public final class AdminEdgeCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminEdgeCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Erzeugt den Service für Router-Admin-Aufrufe.
     *
     * @return Service für Edge-Admin-Operationen
     */
    private AdminEdgeService service() {
        return new AdminEdgeService(ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.adminToken());
    }

    /**
     * Gibt einen Validierungsfehler einheitlich aus.
     *
     * @param pattern Formatmuster
     * @param args Formatargumente
     * @return Exit-Code für Validierungsfehler
     */
    private int validationError(String pattern, Object... args) {
        ConsoleUtils.error(ctx.err(), pattern, args);
        return VALIDATION.code();
    }

    /**
     * Gibt einen technischen Fehler einheitlich aus.
     *
     * @param operation Name der Operation
     * @param message Fehlermeldung
     * @return Exit-Code für technische Fehler
     */
    private int requestFailed(String operation, String message) {
        ConsoleUtils.error(ctx.err(), "[EDGE] %s failed: %s", operation, Objects.toString(message, "unknown error"));
        return REQUEST_FAILED.code();
    }

    /**
     * Gibt eine fachlich abgelehnte Serverantwort einheitlich aus.
     *
     * @param operation Name der Operation
     * @param statusCode HTTP-Status
     * @param body Response-Body
     * @return Exit-Code für fachliche Ablehnungen
     */
    private int rejected(String operation, int statusCode, String body) {
        ConsoleUtils.error(
                ctx.err(), "[EDGE] %s rejected: HTTP %d, body=%s", operation, statusCode, Objects.toString(body, ""));
        return REJECTED.code();
    }

    /**
     * Bewertet ein HTTP-Aufrufergebnis zentral und normiert die Fehlerbehandlung.
     *
     * @param operation Name der Operation
     * @param result Ergebnis des Service-Aufrufs
     * @return normiertes Ergebnisobjekt
     */
    private CallOutcome evaluate(String operation, CallResult result) {
        Objects.requireNonNull(result, "result");

        if (result.error() != null) {
            return CallOutcome.failure(requestFailed(operation, result.error()));
        }

        int statusCode = Objects.requireNonNull(result.statusCode(), "statusCode");
        String body = Objects.toString(result.body(), "");

        if (!result.is2xx()) {
            return CallOutcome.failure(rejected(operation, statusCode, body));
        }

        return CallOutcome.success(statusCode, body);
    }

    /**
     * Gibt bei Bedarf den rohen JSON-Body auf die Konsole aus.
     *
     * @param body Response-Body
     * @return Exit-Code für Erfolg
     */
    private int printRawJson(String body) {
        ctx.out().println(Objects.toString(body, ""));
        ctx.out().flush();
        return SUCCESS.code();
    }

    /**
     * Prüft, ob eine URI eine absolute HTTP- oder HTTPS-URI ist.
     *
     * @param uri zu prüfende URI
     * @return {@code true}, wenn die URI gültig ist, sonst {@code false}
     */
    private static boolean isAbsoluteHttpUri(URI uri) {
        if (uri == null || !uri.isAbsolute() || uri.getScheme() == null) {
            return false;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    /**
     * Prüft, ob ein Text leer oder nur aus Leerzeichen besteht.
     *
     * @param value zu prüfender Text
     * @return {@code true}, wenn der Text leer ist
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Wandelt einen JSON-String in einen {@link JsonNode} um.
     *
     * @param json JSON-String
     * @return geparster JSON-Baum
     */
    private static JsonNode parseJson(String json) {
        return JacksonCodec.fromJson(Objects.toString(json, ""), JsonNode.class);
    }

    /**
     * Liest einen Textwert aus einem JSON-Feld tolerant aus.
     *
     * @param node JSON-Knoten
     * @param field Feldname
     * @param fallback Ersatzwert
     * @return Feldinhalt oder Fallback
     */
    private static String jsonText(JsonNode node, String field, String fallback) {
        return node.path(field).asText(fallback);
    }

    /**
     * Liest einen Long-Wert aus einem JSON-Feld tolerant aus.
     *
     * @param node JSON-Knoten
     * @param field Feldname
     * @param fallback Ersatzwert
     * @return Feldinhalt oder Fallback
     */
    private static long jsonLong(JsonNode node, String field, long fallback) {
        return node.path(field).asLong(fallback);
    }

    /**
     * Liest einen Integer-Wert aus einem JSON-Feld tolerant aus.
     *
     * @param node JSON-Knoten
     * @param field Feldname
     * @param fallback Ersatzwert
     * @return Feldinhalt oder Fallback
     */
    private static int jsonInt(JsonNode node, String field, int fallback) {
        return node.path(field).asInt(fallback);
    }

    /**
     * Normiertes Ergebnis eines bereits ausgewerteten HTTP-Aufrufs.
     *
     * @param successful Kennzeichen für Erfolg
     * @param exitCode zurückzugebender Exit-Code
     * @param statusCode HTTP-Statuscode bei Erfolg
     * @param body HTTP-Body bei Erfolg
     */
    private record CallOutcome(boolean successful, int exitCode, int statusCode, String body) {

        /**
         * Erzeugt ein Erfolgsresultat.
         *
         * @param statusCode HTTP-Statuscode
         * @param body HTTP-Body
         * @return Erfolgsobjekt
         */
        private static CallOutcome success(int statusCode, String body) {
            return new CallOutcome(true, SUCCESS.code(), statusCode, body);
        }

        /**
         * Erzeugt ein Fehlerresultat.
         *
         * @param exitCode Exit-Code
         * @return Fehlerobjekt
         */
        private static CallOutcome failure(int exitCode) {
            return new CallOutcome(false, exitCode, -1, "");
        }
    }

    /**
     * Startet eine verwaltete Edge-Instanz über den Router.
     */
    @Command(
            name = "start",
            description = "Start a managed edge process via router (POST /api/cdn/admin/edges/start).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin edge start --region EU --port 8081 --origin http://localhost:8080 --auto-register=true --wait-ready",
                "  admin edge start --region US --port 8083",
                "  admin edge start -H http://localhost:9090 --region EU --port 8081 --origin http://localhost:8088"
            })
    public static final class AdminEdgeStartCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        /**
         * Basis-URL des Routers.
         */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI host;

        /**
         * Zielregion des zu startenden Edge-Servers.
         */
        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Target region to register the edge under. Example: EU")
        private String region;

        /**
         * HTTP-Port des Edge-Servers.
         */
        @Option(
                names = "--port",
                required = true,
                paramLabel = "PORT",
                description = "TCP port for the edge HTTP server (1..65535). Example: 8081")
        private int port;

        /**
         * Basis-URL des Origin-Servers.
         */
        @Option(
                names = "--origin",
                defaultValue = ORIGIN_URL,
                paramLabel = "ORIGIN_URL",
                description = "Origin base URL passed to the edge (default: ${DEFAULT-VALUE}).")
        private URI originBaseUrl;

        /**
         * Steuert, ob der Router den Edge direkt registriert.
         */
        @Option(
                names = "--auto-register",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router registers the edge in its routing index (default: ${DEFAULT-VALUE}).")
        private boolean autoRegister;

        /**
         * Steuert, ob auf Bereitschaft des Edge gewartet wird.
         */
        @Option(
                names = "--wait-ready",
                defaultValue = "false",
                paramLabel = "true|false",
                description =
                        "If true, router waits until the edge is ready before returning (default: ${DEFAULT-VALUE}).")
        private boolean waitReady;

        /**
         * Gibt bei Aktivierung die rohe JSON-Antwort aus.
         */
        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
        private boolean printJson;

        @Override
        public Integer call() {
            if (AdminEdgeCommand.isBlank(region)) {
                return parent.validationError("[EDGE] region must not be blank");
            }
            if (port <= 0 || port > 65535) {
                return parent.validationError("[EDGE] invalid port: %d (expected 1..65535)", port);
            }
            if (!AdminEdgeCommand.isAbsoluteHttpUri(originBaseUrl)) {
                return parent.validationError("[EDGE] invalid --origin (must be an absolute http/https URI)");
            }
            if (!AdminEdgeCommand.isAbsoluteHttpUri(host)) {
                return parent.validationError("[EDGE] invalid --host (must be an absolute http/https URI)");
            }

            try {
                CallOutcome outcome = parent.evaluate(
                        "start",
                        parent.service().startEdge(host, region, port, originBaseUrl, autoRegister, waitReady));

                if (!outcome.successful()) {
                    return outcome.exitCode();
                }

                if (printJson) {
                    return parent.printRawJson(outcome.body());
                }

                JsonNode json = AdminEdgeCommand.parseJson(outcome.body());
                String instanceId = AdminEdgeCommand.jsonText(json, "instanceId", "n/a");
                String url = AdminEdgeCommand.jsonText(json, "url", "n/a");
                long pid = AdminEdgeCommand.jsonLong(json, "pid", -1);
                String responseRegion = AdminEdgeCommand.jsonText(json, "region", "n/a");

                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[EDGE] started instanceId=%s url=%s pid=%d region=%s",
                        instanceId,
                        url,
                        pid,
                        responseRegion);
                return SUCCESS.code();

            } catch (Exception ex) {
                return parent.requestFailed("start", ex.getMessage());
            }
        }
    }

    /**
     * Stoppt eine verwaltete Edge-Instanz über den Router.
     */
    @Command(
            name = "stop",
            description = "Stop a managed edge process via router (DELETE /api/cdn/admin/edges/{instanceId}).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin edge stop edge-12345 --force",
                "  admin edge stop edge-12345 --force --deregister=false",
                "  admin edge stop -H http://localhost:9090 edge-12345 --force"
            })
    public static final class AdminEdgeStopCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        /**
         * Basis-URL des Routers.
         */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI host;

        /**
         * ID der verwalteten Edge-Instanz.
         */
        @Parameters(
                index = "0",
                paramLabel = "INSTANCE_ID",
                description = "Managed instance id as returned by 'edge start' or 'edge managed'.")
        private String instanceId;

        /**
         * Steuert, ob die Edge aus dem Routing entfernt wird.
         */
        @Option(
                names = "--deregister",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router removes the edge from its routing index (default: ${DEFAULT-VALUE}).")
        private boolean deregister;

        /**
         * Sicherheitsflag für destruktive Stop-Operationen.
         */
        @Option(
                names = "--force",
                defaultValue = "false",
                description = "Safety switch: required to actually stop the process (default: ${DEFAULT-VALUE}).")
        private boolean force;

        @Override
        public Integer call() {
            if (!AdminEdgeCommand.isAbsoluteHttpUri(host)) {
                return parent.validationError("[EDGE] invalid --host (must be an absolute http/https URI)");
            }
            if (AdminEdgeCommand.isBlank(instanceId)) {
                return parent.validationError("[EDGE] instanceId must not be blank");
            }
            if (!force) {
                return parent.validationError(
                        "[EDGE] stop is destructive. Re-run with --force. instanceId=%s", instanceId);
            }

            try {
                CallOutcome outcome = parent.evaluate("stop", parent.service().stopEdge(host, instanceId, deregister));

                if (!outcome.successful()) {
                    return outcome.exitCode();
                }

                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[EDGE] stopped instanceId=%s deregister=%s (HTTP %d)",
                        instanceId,
                        deregister,
                        outcome.statusCode());
                return SUCCESS.code();

            } catch (Exception ex) {
                return parent.requestFailed("stop", ex.getMessage());
            }
        }
    }

    /**
     * Stoppt alle verwalteten Edge-Instanzen einer Region über den Router.
     */
    @Command(
            name = "stop-region",
            description =
                    "Stop all managed edge processes in a region via router (DELETE /api/cdn/admin/edges/region/{region}).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin edge stop-region --region EU --force",
                "  admin edge stop-region --region EU --force --deregister=false",
                "  admin edge stop-region --region EU --force --json"
            })
    public static final class AdminEdgeStopRegionCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        /**
         * Basis-URL des Routers.
         */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI host;

        /**
         * Region, deren Edge-Instanzen gestoppt werden sollen.
         */
        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Region whose managed edges should be stopped. Example: EU")
        private String region;

        /**
         * Steuert, ob die Edges aus dem Routing entfernt werden.
         */
        @Option(
                names = "--deregister",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router removes the edges from its routing index (default: ${DEFAULT-VALUE}).")
        private boolean deregister;

        /**
         * Sicherheitsflag für destruktive Stop-Operationen.
         */
        @Option(
                names = "--force",
                defaultValue = "false",
                description = "Safety switch: required to actually stop all region edges (default: ${DEFAULT-VALUE}).")
        private boolean force;

        /**
         * Gibt bei Aktivierung die rohe JSON-Antwort aus.
         */
        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
        private boolean printJson;

        @Override
        public Integer call() {
            if (!AdminEdgeCommand.isAbsoluteHttpUri(host)) {
                return parent.validationError("[EDGE] invalid --host (must be an absolute http/https URI)");
            }
            if (AdminEdgeCommand.isBlank(region)) {
                return parent.validationError("[EDGE] region must not be blank");
            }
            if (!force) {
                return parent.validationError(
                        "[EDGE] stop-region is destructive. Re-run with --force. region=%s", region);
            }

            try {
                CallOutcome outcome =
                        parent.evaluate("stop-region", parent.service().stopRegion(host, region, deregister));

                if (!outcome.successful()) {
                    return outcome.exitCode();
                }

                if (printJson) {
                    return parent.printRawJson(outcome.body());
                }

                if (!outcome.body().isBlank()) {
                    JsonNode json = AdminEdgeCommand.parseJson(outcome.body());
                    String responseRegion = AdminEdgeCommand.jsonText(json, "region", region);
                    int stopped = AdminEdgeCommand.jsonInt(json, "stopped", -1);

                    ConsoleUtils.info(
                            parent.ctx.out(),
                            "[EDGE] stopped region=%s count=%d deregister=%s (HTTP %d)",
                            responseRegion,
                            stopped,
                            deregister,
                            outcome.statusCode());
                    return SUCCESS.code();
                }

                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[EDGE] stopped region=%s deregister=%s (HTTP %d)",
                        region,
                        deregister,
                        outcome.statusCode());
                return SUCCESS.code();

            } catch (Exception ex) {
                return parent.requestFailed("stop-region", ex.getMessage());
            }
        }
    }

    /**
     * Listet alle vom Router verwalteten Edge-Instanzen auf.
     */
    @Command(
            name = "managed",
            description = "List all edges managed by the router (GET /api/cdn/admin/edges/managed).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin edge managed",
                "  admin edge managed --json",
                "  admin edge managed -H http://localhost:9090"
            })
    public static final class AdminEdgeManagedCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        /**
         * Basis-URL des Routers.
         */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI host;

        /**
         * Gibt bei Aktivierung die rohe JSON-Antwort aus.
         */
        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
        private boolean printJson;

        @Override
        public Integer call() {
            if (!AdminEdgeCommand.isAbsoluteHttpUri(host)) {
                return parent.validationError("[EDGE] invalid --host (must be an absolute http/https URI)");
            }

            try {
                CallOutcome outcome =
                        parent.evaluate("managed", parent.service().listManaged(host));

                if (!outcome.successful()) {
                    return outcome.exitCode();
                }

                if (printJson) {
                    return parent.printRawJson(outcome.body());
                }

                JsonNode edges = AdminEdgeCommand.parseJson(outcome.body());
                if (!edges.isArray() || edges.isEmpty()) {
                    ConsoleUtils.info(parent.ctx.out(), "[EDGE] no managed instances");
                    return SUCCESS.code();
                }

                parent.ctx.out().println("Managed edges:");
                for (JsonNode edge : edges) {
                    String id = AdminEdgeCommand.jsonText(edge, "instanceId", "n/a");
                    String region = AdminEdgeCommand.jsonText(edge, "region", "n/a");
                    String url = AdminEdgeCommand.jsonText(edge, "url", "n/a");
                    long pid = AdminEdgeCommand.jsonLong(edge, "pid", -1);
                    parent.ctx.out().printf("- %s region=%s url=%s pid=%d%n", id, region, url, pid);
                }
                parent.ctx.out().flush();
                return SUCCESS.code();

            } catch (Exception ex) {
                return parent.requestFailed("managed", ex.getMessage());
            }
        }
    }

    /**
     * Startet mehrere verwaltete Edge-Instanzen mit automatischer Portvergabe über den Router.
     */
    @Command(
            name = "auto-start",
            description =
                    "Start multiple managed edge processes via router with auto port allocation (POST /api/cdn/admin/edges/start/auto).",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin edge auto-start --region EU --count 3 --origin http://localhost:8080 --auto-register=true --wait-ready",
                "  admin edge auto-start --region US --count 10",
                "  admin edge auto-start --region EU --count 2 --json"
            })
    public static final class AdminEdgeAutoStartCommand implements Callable<Integer> {

        @ParentCommand
        private AdminEdgeCommand parent;

        /**
         * Basis-URL des Routers.
         */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI host;

        /**
         * Zielregion der zu startenden Edges.
         */
        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Target region to register the edges under. Example: EU")
        private String region;

        /**
         * Anzahl der zu startenden Edge-Prozesse.
         */
        @Option(
                names = "--count",
                required = true,
                paramLabel = "COUNT",
                description = "Number of edge processes to start (must be > 0). Example: 3")
        private int count;

        /**
         * Basis-URL des Origin-Servers.
         */
        @Option(
                names = "--origin",
                defaultValue = ORIGIN_URL,
                paramLabel = "ORIGIN_URL",
                description = "Origin base URL passed to the edges (default: ${DEFAULT-VALUE}).")
        private URI originBaseUrl;

        /**
         * Steuert, ob der Router die Edges direkt registriert.
         */
        @Option(
                names = "--auto-register",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "If true, router registers the edges in its routing index (default: ${DEFAULT-VALUE}).")
        private boolean autoRegister;

        /**
         * Steuert, ob auf die Bereitschaft jeder Edge gewartet wird.
         */
        @Option(
                names = "--wait-ready",
                defaultValue = "false",
                paramLabel = "true|false",
                description =
                        "If true, router waits until each edge is ready before returning (default: ${DEFAULT-VALUE}).")
        private boolean waitReady;

        /**
         * Gibt bei Aktivierung die rohe JSON-Antwort aus.
         */
        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Print raw JSON response body (default: ${DEFAULT-VALUE}).")
        private boolean printJson;

        @Override
        public Integer call() {
            if (AdminEdgeCommand.isBlank(region)) {
                return parent.validationError("[EDGE] auto-start region must not be blank");
            }
            if (count <= 0) {
                return parent.validationError("[EDGE] auto-start invalid --count: %d (expected > 0)", count);
            }
            if (!AdminEdgeCommand.isAbsoluteHttpUri(originBaseUrl)) {
                return parent.validationError(
                        "[EDGE] auto-start invalid --origin (must be an absolute http/https URI)");
            }
            if (!AdminEdgeCommand.isAbsoluteHttpUri(host)) {
                return parent.validationError("[EDGE] invalid --host (must be an absolute http/https URI)");
            }

            try {
                CallOutcome outcome = parent.evaluate(
                        "auto-start",
                        parent.service().startEdgesAuto(host, region, count, originBaseUrl, autoRegister, waitReady));

                if (!outcome.successful()) {
                    return outcome.exitCode();
                }

                if (printJson) {
                    return parent.printRawJson(outcome.body());
                }

                JsonNode json = AdminEdgeCommand.parseJson(outcome.body());
                String responseRegion = AdminEdgeCommand.jsonText(json, "region", "n/a");
                int requested = AdminEdgeCommand.jsonInt(json, "requested", -1);
                int started = AdminEdgeCommand.jsonInt(json, "started", -1);

                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[EDGE] auto-start done region=%s requested=%d started=%d",
                        responseRegion,
                        requested,
                        started);

                JsonNode edges = json.path("edges");
                if (edges.isArray() && !edges.isEmpty()) {
                    parent.ctx.out().println("Started edges:");
                    for (JsonNode edge : edges) {
                        String id = AdminEdgeCommand.jsonText(edge, "instanceId", "n/a");
                        String url = AdminEdgeCommand.jsonText(edge, "url", "n/a");
                        long pid = AdminEdgeCommand.jsonLong(edge, "pid", -1);
                        String edgeRegion = AdminEdgeCommand.jsonText(edge, "region", "n/a");
                        parent.ctx.out().printf("- %s region=%s url=%s pid=%d%n", id, edgeRegion, url, pid);
                    }
                    parent.ctx.out().flush();
                }

                return SUCCESS.code();

            } catch (Exception ex) {
                return parent.requestFailed("auto-start", ex.getMessage());
            }
        }
    }
}
