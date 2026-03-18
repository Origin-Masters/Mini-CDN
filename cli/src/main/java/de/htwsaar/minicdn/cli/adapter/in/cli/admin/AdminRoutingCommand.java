package de.htwsaar.minicdn.cli.adapter.in.cli.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.adapter.in.cli.support.ConsoleUtils;
import de.htwsaar.minicdn.cli.adapter.in.cli.support.JsonUtils;
import de.htwsaar.minicdn.cli.application.admin.AdminRoutingService;
import de.htwsaar.minicdn.cli.application.context.CliContext;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Picocli-Einstieg für die Pflege des Routing-Index über die Router-Admin-API.
 *
 * <p>Das Kommando bündelt Unterbefehle zum Hinzufügen, Entfernen, Anzeigen und
 * Bulk-Aktualisieren von Routing-Einträgen. Die eigentliche Request-Logik liegt
 * im {@link AdminRoutingService}; diese Klasse übernimmt CLI-Parsing, Ausgabe
 * und Exit-Code-Mapping.</p>
 */
@Command(
        name = "routing",
        description = "Manage routing entries via router admin API.",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin routing add --region eu-west --url http://localhost:8081",
            "  admin routing remove --region eu-west --url http://localhost:8081",
            "  admin routing list --check-health",
            "  admin routing bulk --json '[{\"region\":\"eu-west\",\"url\":\"http://localhost:8081\",\"action\":\"add\"}]'"
        },
        subcommands = {
            AdminRoutingCommand.AdminRoutingAddCommand.class,
            AdminRoutingCommand.AdminRoutingRemoveCommand.class,
            AdminRoutingCommand.AdminRoutingListCommand.class,
            AdminRoutingCommand.AdminRoutingBulkCommand.class
        })
public final class AdminRoutingCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt das Root-Kommando für Routing-Admin-Aufrufe.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminRoutingCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /**
     * Gibt die Usage des Kommandos aus, wenn kein Unterbefehl angegeben wurde.
     */
    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Erstellt den fachlichen Service für Routing-Admin-Aufrufe.
     *
     * @return konfigurierte Service-Instanz auf Basis des aktuellen CLI-Kontexts
     */
    private AdminRoutingService service() {
        return new AdminRoutingService(ctx.adminOperations(), ctx.adminToken());
    }

    /**
     * Wertet ein Service-Ergebnis aus und schreibt die passende CLI-Ausgabe.
     *
     * @param result normiertes Request-Ergebnis
     * @param successMessage Fallback-Erfolgsmeldung bei leerem Response-Body
     * @return passender Prozess-Exit-Code
     */
    private int printResult(CallResult result, String successMessage) {
        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[ROUTING] request failed: %s", result.error());
            return REQUEST_FAILED.code();
        }
        if (!result.isRemoteSuccess()) {
            ConsoleUtils.error(
                    ctx.err(),
                    "[ROUTING] request rejected: HTTP %s%s",
                    result.code(),
                    result.body() == null || result.body().isBlank() ? "" : ", body=" + result.body());
            return REJECTED.code();
        }

        if (result.body() != null && !result.body().isBlank()) {
            ctx.out().println(JsonUtils.formatJson(result.body()));
        } else {
            ctx.out().println(successMessage);
        }
        ctx.out().flush();
        return SUCCESS.code();
    }

    @Command(name = "add", description = "Register an edge node for a region.", mixinStandardHelpOptions = true)
    /**
     * Registriert einen Edge-Knoten für eine Region im Routing-Index.
     */
    public static final class AdminRoutingAddCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--url", required = true, description = "Edge base URL.")
        private URI edgeUrl;

        /**
         * Führt das Hinzufügen eines Routing-Eintrags aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis oder Validierungsfehler
         */
        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().addNode(router, region, edgeUrl), "[ROUTING] edge registered successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[ROUTING] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }

    @Command(name = "remove", description = "Remove an edge node from a region.", mixinStandardHelpOptions = true)
    /**
     * Entfernt einen Edge-Knoten aus dem Routing-Index einer Region.
     */
    public static final class AdminRoutingRemoveCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--url", required = true, description = "Edge base URL.")
        private URI edgeUrl;

        /**
         * Führt das Entfernen eines Routing-Eintrags aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis oder Validierungsfehler
         */
        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().removeNode(router, region, edgeUrl), "[ROUTING] edge removed successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[ROUTING] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }

    @Command(name = "list", description = "Show the current routing index.", mixinStandardHelpOptions = true)
    /**
     * Lädt den aktuellen Routing-Index vom Router.
     */
    public static final class AdminRoutingListCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--check-health", defaultValue = "false", description = "Include health checks.")
        private boolean checkHealth;

        /**
         * Führt das Laden des Routing-Index aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis
         */
        @Override
        public Integer call() {
            return parent.printResult(
                    parent.service().listNodes(router, checkHealth), "[ROUTING] routing index loaded");
        }
    }

    @Command(name = "bulk", description = "Execute a bulk routing update.", mixinStandardHelpOptions = true)
    /**
     * Führt eine Bulk-Aktualisierung des Routing-Index aus.
     */
    public static final class AdminRoutingBulkCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--json", description = "Inline JSON array payload for /api/cdn/routes/batches.")
        private String json;

        @Option(names = "--file", description = "Path to a JSON file for /api/cdn/routes/batches.")
        private Path file;

        /**
         * Führt die Bulk-Aktualisierung mit Inline-JSON oder Dateiquelle aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis oder Validierungsfehler
         */
        @Override
        public Integer call() {
            try {
                String payload = resolvePayload();
                return parent.printResult(
                        parent.service().bulkUpdate(router, payload), "[ROUTING] bulk update executed successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[ROUTING] %s", ex.getMessage());
                return VALIDATION.code();
            } catch (Exception ex) {
                ConsoleUtils.error(parent.ctx.err(), "[ROUTING] bulk payload could not be read: %s", ex.getMessage());
                return VALIDATION.code();
            }
        }

        /**
         * Ermittelt den JSON-Payload aus genau einer der beiden Quellen {@code --json} oder {@code --file}.
         *
         * @return bereinigter JSON-Payload für den Bulk-Endpunkt
         * @throws Exception wenn die Dateiquelle nicht gelesen werden kann
         */
        private String resolvePayload() throws Exception {
            boolean hasJson = json != null && !json.isBlank();
            boolean hasFile = file != null;

            if (hasJson == hasFile) {
                throw new IllegalArgumentException("use exactly one of --json or --file");
            }

            if (hasJson) {
                return json.trim();
            }

            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("bulk file does not exist: " + file);
            }
            return Files.readString(file).trim();
        }
    }
}
