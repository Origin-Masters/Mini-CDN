package de.htwsaar.minicdn.cli.command.root;

import de.htwsaar.minicdn.cli.command.admin.AdminCommand;
import de.htwsaar.minicdn.cli.command.user.UserCommand;
import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Root-Command des CLI-Kommandobaums.
 *
 * <p>Aufgaben:
 * - Definiert Name, Beschreibung und globale Help-Optionen der CLI.
 * - Registriert Top-Level-Subcommands (z. B. {@code admin}, {@code user}).
 * - Definiert das Default-Verhalten, wenn ohne Subcommand aufgerufen wird.
 */
@Command(
        name = "minicdn",
        description = "Mini-CDN CLI",
        mixinStandardHelpOptions = true,
        subcommands = {AdminCommand.class, UserCommand.class, HelpCommand.class})
public final class MiniCdnRootCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Terminal, Output, HTTP-Client, Timeouts, ...)
     */
    public MiniCdnRootCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /**
     * Default-Aktion, wenn kein Subcommand angegeben ist.
     *
     * <p>Zeigt die Usage der Root-CLI und gibt einen kurzen Hinweis auf Help
     * bzw. den interaktiven Modus.
     */
    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().println();
        ctx.out().println("Tipp: Verwende `minicdn help <command>` oder starte ohne Args für die interaktive Shell.");
        ctx.out().flush();
    }
}
