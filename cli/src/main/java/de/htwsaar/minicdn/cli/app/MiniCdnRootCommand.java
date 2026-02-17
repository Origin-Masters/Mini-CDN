package de.htwsaar.minicdn.cli.app;

import de.htwsaar.minicdn.cli.AdminCommand;
import de.htwsaar.minicdn.cli.UserCommand;
import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

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

    public MiniCdnRootCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx);
    }

    /**
     * Default-Aktion, wenn kein Subcommand angegeben ist.
     *
     * <p>Hinweis: In non-interactive Mode wird bewusst keine Aktion ausgeführt,
     * sondern nur auf Help bzw. den interaktiven Modus hingewiesen.
     */
    @Override
    public void run() {
        System.out.println("Use -h for help or start without args for interactive shell.");
    }
}
