package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * User-Commands für Statistiken des aktuellen Nutzers.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 */
@Command(
        name = "stats",
        description = "Statistics for the current user",
        subcommands = {UserStatsCommand.UserStatsResourceCommand.class})
public final class UserStatsCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public UserStatsCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(name = "resource", description = "Show stats for one of my resources")
    public static final class UserStatsResourceCommand implements Runnable {

        @ParentCommand
        private UserStatsCommand parent;

        @Option(names = "--resource-id", required = true, description = "Resource ID")
        private long resourceId;

        @Override
        public void run() {
            // TODO: StatsService.resourceStatsForCurrentUser(resourceId)
            parent.ctx.out().printf("[USER] Stats for my resource %d%n", resourceId);
            parent.ctx.out().flush();
        }
    }
}
