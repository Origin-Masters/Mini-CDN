package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminRoutingService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
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
 * Stellt Admin-Befehle zur Pflege des Routing-Index bereit.
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

    public AdminRoutingCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    private AdminRoutingService service() {
        return new AdminRoutingService(ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.adminToken());
    }

    private int printResult(CallResult result, String successMessage) {
        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[ROUTING] request failed: %s", result.error());
            return REQUEST_FAILED.code();
        }
        if (!result.is2xx()) {
            ConsoleUtils.error(
                    ctx.err(),
                    "[ROUTING] request rejected: HTTP %s%s",
                    result.statusCode(),
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
    public static final class AdminRoutingAddCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--url", required = true, description = "Edge base URL.")
        private URI edgeUrl;

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
    public static final class AdminRoutingRemoveCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--url", required = true, description = "Edge base URL.")
        private URI edgeUrl;

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
    public static final class AdminRoutingListCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--check-health", defaultValue = "false", description = "Include health checks.")
        private boolean checkHealth;

        @Override
        public Integer call() {
            return parent.printResult(
                    parent.service().listNodes(router, checkHealth), "[ROUTING] routing index loaded");
        }
    }

    @Command(name = "bulk", description = "Execute a bulk routing update.", mixinStandardHelpOptions = true)
    public static final class AdminRoutingBulkCommand implements Callable<Integer> {

        @ParentCommand
        private AdminRoutingCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--json", description = "Inline JSON array payload for /api/cdn/routing/bulk.")
        private String json;

        @Option(names = "--file", description = "Path to a JSON file for /api/cdn/routing/bulk.")
        private Path file;

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
