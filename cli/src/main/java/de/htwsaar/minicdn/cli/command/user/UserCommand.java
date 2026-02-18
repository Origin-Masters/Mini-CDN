package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Gruppiert User-Operationen der CLI.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 */
@Command(
        name = "user",
        description = "User operations",
        subcommands = {UserResourceCommand.class, UserCacheCommand.class, UserFileCommand.class, UserStatsCommand.class
        })
public final class UserCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public UserCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }
}
