package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminConfigService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Remote-Live-Konfiguration für Origin- und Edge-Server.
 */
@Command(
        name = "config",
        description = "Remote runtime configuration for Origin and Edge services.",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin config origin show --origin http://localhost:8080",
            "  admin config origin set --origin http://localhost:8080 --max-upload-bytes 1048576",
            "  admin config edge show --edge http://localhost:8081",
            "  admin config edge set --edge http://localhost:8081 --default-ttl-ms 120000 --max-entries 200"
        },
        subcommands = {AdminConfigCommand.OriginConfigCommand.class, AdminConfigCommand.EdgeConfigCommand.class})
public final class AdminConfigCommand implements Runnable {

    final CliContext ctx;

    public AdminConfigCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        ConsoleUtils.info(ctx.out(), new CommandLine(this).getUsageMessage());
    }

    AdminConfigService service() {
        return new AdminConfigService(ctx.transportClient(), ctx.defaultRequestTimeout());
    }

    @Command(
            name = "origin",
            description = "Manage runtime config of an Origin server.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config origin show --origin http://localhost:8080",
                "  admin config origin set --origin http://localhost:8080 --log-level DEBUG",
                "  admin config origin set --origin http://localhost:8080 --max-upload-bytes 5242880"
            },
            subcommands = {OriginShowCommand.class, OriginSetCommand.class})
    public static final class OriginConfigCommand implements Runnable {

        @ParentCommand
        private AdminConfigCommand parent;

        @Override
        public void run() {
            ConsoleUtils.info(parent.ctx.out(), new CommandLine(this).getUsageMessage());
        }
    }

    @Command(
            name = "show",
            description = "Show current Origin runtime config.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin config origin show --origin http://localhost:8080"})
    public static final class OriginShowCommand implements Callable<Integer> {

        @ParentCommand
        private OriginConfigCommand parent;

        @Option(names = "--origin", required = true, description = "Origin base URL. Example: http://localhost:8080")
        private URI origin;

        @Override
        public Integer call() {
            return printResult(parent.parent.service().getOriginConfig(origin), parent.parent.ctx);
        }
    }

    @Command(
            name = "set",
            description = "Patch Origin runtime config without restart.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config origin set --origin http://localhost:8080 --max-upload-bytes 1048576",
                "  admin config origin set --origin http://localhost:8080 --log-level INFO"
            })
    public static final class OriginSetCommand implements Callable<Integer> {

        @ParentCommand
        private OriginConfigCommand parent;

        @Option(names = "--origin", required = true, description = "Origin base URL. Example: http://localhost:8080")
        private URI origin;

        @Option(names = "--max-upload-bytes", description = "Maximum upload size in bytes. Example: 1048576")
        private Long maxUploadBytes;

        @Option(names = "--log-level", description = "Root log level. Example: DEBUG")
        private String logLevel;

        @Override
        public Integer call() {
            return printResult(
                    parent.parent.service().patchOriginConfig(origin, maxUploadBytes, logLevel), parent.parent.ctx);
        }
    }

    @Command(
            name = "edge",
            description = "Manage runtime config of an Edge server.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config edge show --edge http://localhost:8081",
                "  admin config edge set --edge http://localhost:8081 --default-ttl-ms 120000",
                "  admin config edge set --edge http://localhost:8081 --replacement-strategy LFU",
                "  admin config edge ttl set --edge http://localhost:8081 --prefix videos/ --ttl-ms 10000"
            },
            subcommands = {EdgeShowCommand.class, EdgeSetCommand.class, EdgeTtlCommand.class})
    public static final class EdgeConfigCommand implements Runnable {

        @ParentCommand
        private AdminConfigCommand parent;

        @Override
        public void run() {
            ConsoleUtils.info(parent.ctx.out(), new CommandLine(this).getUsageMessage());
        }
    }

    @Command(
            name = "show",
            description = "Show current Edge runtime config.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin config edge show --edge http://localhost:8081"})
    public static final class EdgeShowCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeConfigCommand parent;

        @Option(names = "--edge", required = true, description = "Edge base URL. Example: http://localhost:8081")
        private URI edge;

        @Override
        public Integer call() {
            return printResult(parent.parent.service().getEdgeConfig(edge), parent.parent.ctx);
        }
    }

    @Command(
            name = "set",
            description = "Patch Edge runtime config without restart.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin config edge set --edge http://localhost:8081 --default-ttl-ms 120000 --max-entries 300",
                "  admin config edge set --edge http://localhost:8081 --region EU --replacement-strategy LRU"
            })
    public static final class EdgeSetCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeConfigCommand parent;

        @Option(names = "--edge", required = true, description = "Edge base URL. Example: http://localhost:8081")
        private URI edge;

        @Option(names = "--region", description = "Edge region value. Example: EU")
        private String region;

        @Option(names = "--default-ttl-ms", description = "Default cache TTL in ms. Example: 120000")
        private Long defaultTtlMs;

        @Option(names = "--max-entries", description = "Max cache entries. Example: 200")
        private Integer maxEntries;

        @Option(names = "--replacement-strategy", description = "Replacement strategy: LRU or LFU. Example: LFU")
        private String replacementStrategy;

        @Override
        public Integer call() {
            return printResult(
                    parent.parent
                            .service()
                            .patchEdgeConfig(edge, region, defaultTtlMs, maxEntries, replacementStrategy),
                    parent.parent.ctx);
        }
    }

    @Command(
            name = "ttl",
            description = "Manage TTL prefix policies on an Edge server.",
            mixinStandardHelpOptions = true,
            subcommands = {EdgeTtlShowCommand.class, EdgeTtlSetCommand.class, EdgeTtlRemoveCommand.class})
    public static final class EdgeTtlCommand implements Runnable {

        @ParentCommand
        private EdgeConfigCommand parent;

        @Override
        public void run() {
            ConsoleUtils.info(parent.parent.ctx.out(), new CommandLine(this).getUsageMessage());
        }
    }

    @Command(name = "show", description = "Show TTL prefix policies.", mixinStandardHelpOptions = true)
    public static final class EdgeTtlShowCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeTtlCommand parent;

        @Option(names = "--edge", required = true, description = "Edge base URL. Example: http://localhost:8081")
        private URI edge;

        @Override
        public Integer call() {
            return printResult(parent.parent.parent.service().getEdgeTtlPolicies(edge), parent.parent.parent.ctx);
        }
    }

    @Command(name = "set", description = "Set TTL policy for a path prefix.", mixinStandardHelpOptions = true)
    public static final class EdgeTtlSetCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeTtlCommand parent;

        @Option(names = "--edge", required = true, description = "Edge base URL. Example: http://localhost:8081")
        private URI edge;

        @Option(names = "--prefix", required = true, description = "Path prefix. Example: videos/")
        private String prefix;

        @Option(names = "--ttl-ms", required = true, description = "TTL in ms. Example: 30000")
        private Long ttlMs;

        @Override
        public Integer call() {
            return printResult(
                    parent.parent.parent.service().setEdgeTtlPolicy(edge, prefix, ttlMs), parent.parent.parent.ctx);
        }
    }

    @Command(name = "remove", description = "Remove TTL policy for a path prefix.", mixinStandardHelpOptions = true)
    public static final class EdgeTtlRemoveCommand implements Callable<Integer> {

        @ParentCommand
        private EdgeTtlCommand parent;

        @Option(names = "--edge", required = true, description = "Edge base URL. Example: http://localhost:8081")
        private URI edge;

        @Option(names = "--prefix", required = true, description = "Path prefix. Example: videos/")
        private String prefix;

        @Override
        public Integer call() {
            return printResult(
                    parent.parent.parent.service().removeEdgeTtlPolicy(edge, prefix), parent.parent.parent.ctx);
        }
    }

    private static Integer printResult(HttpCallResult result, CliContext ctx) {
        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[CONFIG] request failed: %s", result.error());
            return 1;
        }

        int status = Objects.requireNonNull(result.statusCode(), "statusCode");
        String body = Objects.toString(result.body(), "");

        if (status >= 200 && status < 300) {
            ConsoleUtils.info(ctx.out(), "[CONFIG] success (HTTP %d)", status);
            if (!body.isBlank()) {
                ctx.out().println(body);
            }
            ctx.out().flush();
            return 0;
        }

        ConsoleUtils.error(ctx.err(), "[CONFIG] rejected (HTTP %d): %s", status, body);
        return 2;
    }
}
