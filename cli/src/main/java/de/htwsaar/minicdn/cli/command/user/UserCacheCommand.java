package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * User-Commands für Cache-Operationen des aktuellen Nutzers.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 */
@Command(
        name = "cache",
        description = "Cache operations for the current user",
        subcommands = {UserCacheCommand.UserCachePurgeCommand.class})
public final class UserCacheCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public UserCacheCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(name = "purge", description = "Purge cache for one of my resources")
    public static final class UserCachePurgeCommand implements Runnable {

        @ParentCommand
        private UserCacheCommand parent;

        @Option(names = "--resource-id", required = true, description = "Resource ID")
        private long resourceId;

        @Override
        public void run() {
            // TODO: CacheService.purgeForCurrentUser(resourceId)
            parent.ctx.out().printf("[USER] Purge cache for my resource %d%n", resourceId);
            parent.ctx.out().flush();
        }
    }
}
