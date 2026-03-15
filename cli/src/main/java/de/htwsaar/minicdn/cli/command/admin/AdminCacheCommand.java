package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminCacheService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Stellt Admin-Befehle zur Cache-Invalidierung über den Router bereit.
 */
@Command(
        name = "cache",
        description = "Invalidate edge caches via router admin API.",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin cache file --region eu-west --path videos/intro.mp4",
            "  admin cache prefix --region eu-west --value videos/",
            "  admin cache clear --region eu-west"
        },
        subcommands = {
            AdminCacheCommand.AdminCacheInvalidateFileCommand.class,
            AdminCacheCommand.AdminCacheInvalidatePrefixCommand.class,
            AdminCacheCommand.AdminCacheClearRegionCommand.class
        })
public final class AdminCacheCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    public AdminCacheCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    private AdminCacheService service() {
        return new AdminCacheService(ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.adminToken());
    }

    private int printResult(CallResult result, String successMessage) {
        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[CACHE] request failed: %s", result.error());
            return REQUEST_FAILED.code();
        }
        if (!result.is2xx()) {
            ConsoleUtils.error(
                    ctx.err(),
                    "[CACHE] request rejected: HTTP %s%s",
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

    @Command(
            name = "file",
            description = "Invalidate a single cached file in a region.",
            mixinStandardHelpOptions = true)
    public static final class AdminCacheInvalidateFileCommand implements Callable<Integer> {

        @ParentCommand
        private AdminCacheCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--path", required = true, description = "Relative file path to invalidate.")
        private String path;

        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().invalidateFile(router, region, path), "[CACHE] file invalidated successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[CACHE] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }

    @Command(
            name = "prefix",
            description = "Invalidate all cached files of a prefix in a region.",
            mixinStandardHelpOptions = true)
    public static final class AdminCacheInvalidatePrefixCommand implements Callable<Integer> {

        @ParentCommand
        private AdminCacheCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--value", required = true, description = "Prefix to invalidate.")
        private String value;

        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().invalidatePrefix(router, region, value),
                        "[CACHE] prefix invalidated successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[CACHE] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }

    @Command(name = "clear", description = "Clear the full cache of a region.", mixinStandardHelpOptions = true)
    public static final class AdminCacheClearRegionCommand implements Callable<Integer> {

        @ParentCommand
        private AdminCacheCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().clearRegion(router, region), "[CACHE] region cache cleared successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[CACHE] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }
}
