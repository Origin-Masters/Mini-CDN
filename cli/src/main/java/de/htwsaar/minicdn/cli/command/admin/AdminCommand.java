package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Top-Level Admin-Command, gruppiert alle Admin-Subcommands.
 *
 * <p>Wird über {@link de.htwsaar.minicdn.cli.di.ContextFactory} instanziiert, damit alle Subcommands
 * konsistent Zugriff auf den {@link CliContext} haben.
 */
@Command(
        name = "admin",
        description = "mini-Cdn Administration",
        subcommands = {
            AdminResourceCommand.class,
            AdminNodeCommand.class,
            AdminUserMgmtCommand.class,
            AdminConfigCommand.class,
            PingCommand.class,
            AdminStatsCommand.class
        })
public final class AdminCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    public AdminCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }
}
