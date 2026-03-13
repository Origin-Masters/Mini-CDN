package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.EDGE_URL;
import static de.htwsaar.minicdn.common.util.DefaultsURL.ORIGIN_URL;
import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminConfigService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.common.util.PathUtils;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Stellt Admin-Befehle zur Laufzeitkonfiguration von Origin- und Edge-Servern bereit.
 *
 * <p>Die Klasse bildet die CLI-Adapter-Schicht für Konfigurationsoperationen.
 * Sie nimmt Benutzereingaben entgegen, normalisiert diese bei Bedarf und delegiert
 * die eigentliche Fachoperation an den {@link AdminConfigService}. Dadurch bleibt
 * die Transport- und Protokolllogik außerhalb der Command-Klassen gekapselt.</p>
 */
@Command(
        name = "config",
        description = "Remote runtime configuration for Origin and Edge services.",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin config origin show",
            "  admin config origin set --max-upload-bytes 1048576",
            "  admin config origin spare show",
            "  admin config origin spare add --url http://localhost:8084",
            "  admin config origin show --origin http://anderer-origin:8080",
            "  admin config edge show",
            "  admin config edge set --default-ttl-ms 120000 --max-entries 200",
            "  admin config edge show --edge http://anderer-edge:8081"
        },
        subcommands = {AdminConfigCommand.OriginConfigCommand.class, AdminConfigCommand.EdgeConfigCommand.class})
public final class AdminConfigCommand implements Runnable {

    /**
     * Gemeinsamer CLI-Kontext.
     */
    final CliContext ctx;

    /**
     * Fachlicher Service für Remote-Konfigurationsaufrufe.
     */
    private final AdminConfigService adminConfigService;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminConfigCommand(CliContext ctx) {
        this(
                ctx,
                new AdminConfigService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(),
                        ctx.defaultRequestTimeout(),
                        ctx.adminToken()));
    }

    /**
     * Erzeugt den Command mit explizit übergebenem Service.
     *
     * <p>Dieser Konstruktor erleichtert Tests und hält die Abhängigkeiten explizit.</p>
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param adminConfigService fachlicher Service für Konfigurationsaufrufe
     */
    AdminConfigCommand(CliContext ctx, AdminConfigService adminConfigService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.adminConfigService = Objects.requireNonNull(adminConfigService, "adminConfigService");
    }

    @Override
    public void run() {
        printUsage(this);
    }

    /**
     * Liefert den Service für Remote-Konfigurationsaufrufe.
     *
     * @return Service für Admin-Konfiguration
     */
    AdminConfigService service() {
        return adminConfigService;
    }

    /**
     * Gibt die Usage-Hilfe für einen Command aus.
     *
     * @param command Command-Instanz, für die die Hilfe ausgegeben werden soll
     */
    void printUsage(Object command) {
        ConsoleUtils.info(ctx.out(), new CommandLine(command).getUsageMessage());
    }

    /**
     * Verarbeitet ein HTTP-Ergebnis einheitlich und gibt den passenden Exit-Code zurück.
     *
     * @param result Ergebnis des Remote-Aufrufs
     * @return SUCCESS bei Erfolg, REQUEST_FAILED bei technischem Fehler,
     *         REJECTED bei fachlicher Ablehnung
     */
    int printResult(CallResult result) {
        Objects.requireNonNull(result, "result");

        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[CONFIG] request failed: %s", result.error());
            return REQUEST_FAILED.code();
        }

        int status = Objects.requireNonNull(result.statusCode(), "statusCode");
        String body = Objects.toString(result.body(), "");

        if (result.is2xx()) {
            ConsoleUtils.info(ctx.out(), "[CONFIG] success (HTTP %d)", status);
            if (!body.isBlank()) {
                ctx.out().println(body);
            }
            ctx.out().flush();
            return SUCCESS.code();
        }

        ConsoleUtils.error(ctx.err(), "[CONFIG] rejected (HTTP %d): %s", status, body);
        return REJECTED.code();
    }

    /**
     * Normalisiert ein TTL-Präfix auf einen sicheren relativen Pfad.
     *
     * <p>Damit wird dieselbe Common-Regel verwendet wie in anderen Modulen.
     * Führende Slashes, doppelte Slashes und unsichere Segmente werden dadurch
     * konsistent behandelt.</p>
     *
     * @param rawPrefix vom Benutzer eingegebenes Präfix
     * @return normalisiertes Präfix
     * @throws IllegalArgumentException wenn das Präfix leer oder unsicher ist
     */
    String normalizeTtlPrefix(String rawPrefix) {
        return PathUtils.normalizeRelativePath(rawPrefix);
    }

    /**
     * Gibt einen Validierungsfehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return VALIDATION
     */
    int validationError(String message) {
        ConsoleUtils.error(ctx.err(), "[CONFIG] invalid input: %s", message);
        return VALIDATION.code();
    }

    /**
     * Gruppiert Befehle zur Laufzeitkonfiguration des Origin-Servers.
     */
    @Command(
            name = "origin",
            description = "Manage runtime config of an Origin server.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config origin show",
                "  admin config origin set --log-level DEBUG",
                "  admin config origin spare show",
                "  admin config origin spare failover-check",
                "  admin config origin set --max-upload-bytes 5242880",
                "  admin config origin show --origin http://anderer-origin:8080"
            },
            subcommands = {OriginShowCommand.class, OriginSetCommand.class, OriginSpareCommand.class})
    public static final class OriginConfigCommand implements Runnable {

        @ParentCommand
        private AdminConfigCommand parent;

        /**
         * Liefert den Root-Command.
         *
         * @return Root-Command
         */
        AdminConfigCommand root() {
            return parent;
        }

        @Override
        public void run() {
            root().printUsage(this);
        }
    }

    /**
     * Zeigt die aktuelle Laufzeitkonfiguration des Origin-Servers an.
     */
    @Command(
            name = "show",
            description = "Show current Origin runtime config.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin config origin show", "  admin config origin show --origin http://anderer-origin:8080"})
    public static final class OriginShowCommand implements Callable<Integer> {

        @ParentCommand
        private OriginConfigCommand parent;

        /**
         * Basis-URL des Origin-Servers.
         */
        @Option(
                names = "--origin",
                defaultValue = ORIGIN_URL,
                description = "Origin base URL. Standard: ${DEFAULT-VALUE}")
        private URI origin;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().getOriginConfig(origin));
        }
    }

    /**
     * Aktualisiert Teile der Laufzeitkonfiguration des Origin-Servers ohne Neustart.
     */
    @Command(
            name = "set",
            description = "Patch Origin runtime config without restart.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config origin set --max-upload-bytes 1048576",
                "  admin config origin set --log-level INFO",
                "  admin config origin set --origin http://anderer-origin:8080 --log-level DEBUG"
            })
    public static final class OriginSetCommand implements Callable<Integer> {

        @ParentCommand
        private OriginConfigCommand parent;

        /**
         * Basis-URL des Origin-Servers.
         */
        @Option(
                names = "--origin",
                defaultValue = ORIGIN_URL,
                description = "Origin base URL. Standard: ${DEFAULT-VALUE}")
        private URI origin;

        /**
         * Maximale Upload-Größe in Bytes.
         */
        @Option(names = "--max-upload-bytes", description = "Maximum upload size in bytes. Example: 1048576")
        private Long maxUploadBytes;

        /**
         * Root-Log-Level des Servers.
         */
        @Option(names = "--log-level", description = "Root log level. Example: DEBUG")
        private String logLevel;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().patchOriginConfig(origin, maxUploadBytes, logLevel));
        }
    }

    /**
     * Gruppiert Befehle zur Verwaltung von Origin-Hot-Spares über den Router.
     */
    @Command(
            name = "spare",
            description = "Manage Origin hot spares via router admin API.",
            mixinStandardHelpOptions = true,
            subcommands = {
                OriginSpareShowCommand.class,
                OriginSpareAddCommand.class,
                OriginSpareRemoveCommand.class,
                OriginSparePromoteCommand.class,
                OriginSpareFailoverCheckCommand.class
            })
    public static final class OriginSpareCommand implements Runnable {

        @ParentCommand
        private OriginConfigCommand parent;

        AdminConfigCommand root() {
            return parent.root();
        }

        @Override
        public void run() {
            root().printUsage(this);
        }
    }

    @Command(
            name = "show",
            description = "Show active Origin and registered hot spares.",
            mixinStandardHelpOptions = true)
    public static final class OriginSpareShowCommand implements Callable<Integer> {

        @ParentCommand
        private OriginSpareCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                description = "Router base URL. Standard: ${DEFAULT-VALUE}")
        private URI router;

        @Option(names = "--check-health", defaultValue = "false", description = "Include live health status.")
        private boolean checkHealth;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().getOriginCluster(router, checkHealth));
        }
    }

    @Command(name = "add", description = "Register a new Origin hot spare.", mixinStandardHelpOptions = true)
    public static final class OriginSpareAddCommand implements Callable<Integer> {

        @ParentCommand
        private OriginSpareCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                description = "Router base URL. Standard: ${DEFAULT-VALUE}")
        private URI router;

        @Option(names = "--url", required = true, description = "Spare Origin base URL. Example: http://localhost:8084")
        private URI url;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().addOriginSpare(router, url));
        }
    }

    @Command(name = "remove", description = "Remove a registered Origin hot spare.", mixinStandardHelpOptions = true)
    public static final class OriginSpareRemoveCommand implements Callable<Integer> {

        @ParentCommand
        private OriginSpareCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                description = "Router base URL. Standard: ${DEFAULT-VALUE}")
        private URI router;

        @Option(names = "--url", required = true, description = "Spare Origin base URL. Example: http://localhost:8084")
        private URI url;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().removeOriginSpare(router, url));
        }
    }

    @Command(name = "promote", description = "Promote a hot spare to active Origin.", mixinStandardHelpOptions = true)
    public static final class OriginSparePromoteCommand implements Callable<Integer> {

        @ParentCommand
        private OriginSpareCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                description = "Router base URL. Standard: ${DEFAULT-VALUE}")
        private URI router;

        @Option(names = "--url", required = true, description = "Spare Origin base URL to promote.")
        private URI url;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().promoteOriginSpare(router, url));
        }
    }

    @Command(
            name = "failover-check",
            description = "Force an immediate active-Origin failover check.",
            mixinStandardHelpOptions = true)
    public static final class OriginSpareFailoverCheckCommand implements Callable<Integer> {

        @ParentCommand
        private OriginSpareCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                description = "Router base URL. Standard: ${DEFAULT-VALUE}")
        private URI router;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().checkOriginFailover(router));
        }
    }

    /**
     * Gruppiert Befehle zur Laufzeitkonfiguration des Edge-Servers.
     */
    @Command(
            name = "edge",
            description = "Manage runtime config of an Edge server.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config edge show",
                "  admin config edge set --default-ttl-ms 120000",
                "  admin config edge set --replacement-strategy LFU",
                "  admin config edge ttl set --prefix videos/ --ttl-ms 10000",
                "  admin config edge show --edge http://anderer-edge:8081"
            },
            subcommands = {EdgeShowCommand.class, EdgeSetCommand.class, EdgeTtlCommand.class})
    public static final class EdgeConfigCommand implements Runnable {

        @ParentCommand
        private AdminConfigCommand parent;

        /**
         * Liefert den Root-Command.
         *
         * @return Root-Command
         */
        AdminConfigCommand root() {
            return parent;
        }

        @Override
        public void run() {
            root().printUsage(this);
        }
    }

    /**
     * Zeigt die aktuelle Laufzeitkonfiguration des Edge-Servers an.
     */
    @Command(
            name = "show",
            description = "Show current Edge runtime config.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin config edge show", "  admin config edge show --edge http://anderer-edge:8081"})
    public static final class EdgeShowCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeConfigCommand parent;

        /**
         * Basis-URL des Edge-Servers.
         */
        @Option(names = "--edge", defaultValue = EDGE_URL, description = "Edge base URL. Standard: ${DEFAULT-VALUE}")
        private URI edge;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().getEdgeConfig(edge));
        }
    }

    /**
     * Aktualisiert Teile der Laufzeitkonfiguration des Edge-Servers ohne Neustart.
     */
    @Command(
            name = "set",
            description = "Patch Edge runtime config without restart.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config edge set --default-ttl-ms 120000 --max-entries 300",
                "  admin config edge set --region EU --replacement-strategy LRU",
                "  admin config edge set --edge http://anderer-edge:8081 --max-entries 500"
            })
    public static final class EdgeSetCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeConfigCommand parent;

        /**
         * Basis-URL des Edge-Servers.
         */
        @Option(names = "--edge", defaultValue = EDGE_URL, description = "Edge base URL. Standard: ${DEFAULT-VALUE}")
        private URI edge;

        /**
         * Region des Edge-Servers.
         */
        @Option(names = "--region", description = "Edge region value. Example: EU")
        private String region;

        /**
         * Standard-TTL des Caches in Millisekunden.
         */
        @Option(names = "--default-ttl-ms", description = "Default cache TTL in ms. Example: 120000")
        private Long defaultTtlMs;

        /**
         * Maximale Anzahl an Cache-Einträgen.
         */
        @Option(names = "--max-entries", description = "Max cache entries. Example: 200")
        private Integer maxEntries;

        /**
         * Ersetzungsstrategie des Caches.
         */
        @Option(names = "--replacement-strategy", description = "Replacement strategy: LRU or LFU. Example: LFU")
        private String replacementStrategy;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(
                    root.service().patchEdgeConfig(edge, region, defaultTtlMs, maxEntries, replacementStrategy));
        }
    }

    /**
     * Gruppiert Befehle zur Verwaltung von TTL-Präfixregeln auf einem Edge-Server.
     */
    @Command(
            name = "ttl",
            description = "Manage TTL prefix policies on an Edge server.",
            mixinStandardHelpOptions = true,
            subcommands = {EdgeTtlShowCommand.class, EdgeTtlSetCommand.class, EdgeTtlRemoveCommand.class})
    public static final class EdgeTtlCommand implements Runnable {

        @ParentCommand
        private EdgeConfigCommand parent;

        /**
         * Liefert den Root-Command.
         *
         * @return Root-Command
         */
        AdminConfigCommand root() {
            return parent.root();
        }

        @Override
        public void run() {
            root().printUsage(this);
        }
    }

    /**
     * Zeigt alle TTL-Präfixregeln des Edge-Servers an.
     */
    @Command(name = "show", description = "Show TTL prefix policies.", mixinStandardHelpOptions = true)
    public static final class EdgeTtlShowCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeTtlCommand parent;

        /**
         * Basis-URL des Edge-Servers.
         */
        @Option(names = "--edge", defaultValue = EDGE_URL, description = "Edge base URL. Standard: ${DEFAULT-VALUE}")
        private URI edge;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            return root.printResult(root.service().getEdgeTtlPolicies(edge));
        }
    }

    /**
     * Setzt eine TTL-Regel für ein Pfad-Präfix.
     */
    @Command(name = "set", description = "Set TTL policy for a path prefix.", mixinStandardHelpOptions = true)
    public static final class EdgeTtlSetCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeTtlCommand parent;

        /**
         * Basis-URL des Edge-Servers.
         */
        @Option(names = "--edge", defaultValue = EDGE_URL, description = "Edge base URL. Standard: ${DEFAULT-VALUE}")
        private URI edge;

        /**
         * Pfad-Präfix, für das die Regel gelten soll.
         */
        @Option(names = "--prefix", required = true, description = "Path prefix. Example: videos/")
        private String prefix;

        /**
         * TTL in Millisekunden.
         */
        @Option(names = "--ttl-ms", required = true, description = "TTL in ms. Example: 30000")
        private Long ttlMs;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            try {
                String normalizedPrefix = root.normalizeTtlPrefix(prefix);
                return root.printResult(root.service().setEdgeTtlPolicy(edge, normalizedPrefix, ttlMs));
            } catch (IllegalArgumentException ex) {
                return root.validationError(ex.getMessage());
            }
        }
    }

    /**
     * Entfernt eine TTL-Regel für ein Pfad-Präfix.
     */
    @Command(name = "remove", description = "Remove TTL policy for a path prefix.", mixinStandardHelpOptions = true)
    public static final class EdgeTtlRemoveCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeTtlCommand parent;

        /**
         * Basis-URL des Edge-Servers.
         */
        @Option(names = "--edge", defaultValue = EDGE_URL, description = "Edge base URL. Standard: ${DEFAULT-VALUE}")
        private URI edge;

        /**
         * Pfad-Präfix, dessen Regel entfernt werden soll.
         */
        @Option(names = "--prefix", required = true, description = "Path prefix. Example: videos/")
        private String prefix;

        @Override
        public Integer call() {
            AdminConfigCommand root = parent.root();
            try {
                String normalizedPrefix = root.normalizeTtlPrefix(prefix);
                return root.printResult(root.service().removeEdgeTtlPolicy(edge, normalizedPrefix));
            } catch (IllegalArgumentException ex) {
                return root.validationError(ex.getMessage());
            }
        }
    }
}
