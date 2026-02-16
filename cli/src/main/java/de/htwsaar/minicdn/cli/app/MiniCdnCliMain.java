package de.htwsaar.minicdn.cli.app;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.di.ContextFactory;
import de.htwsaar.minicdn.cli.shell.MiniCdnInteractiveShell;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.time.Duration;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/**
 * Einstiegspunkt der Mini-CDN CLI-Anwendung.
 *
 * <p>Aufgaben:
 * - Initialisiert Terminal-I/O (JLine) sowie gemeinsame Infrastruktur (HTTP-Client, Standard-Timeouts).
 * - Baut die Picocli-Command-Struktur inkl. {@link de.htwsaar.minicdn.cli.di.ContextFactory} für Constructor Injection.
 * - Wählt den Modus: einmalige Ausführung (Args vorhanden) oder interaktive Shell (keine Args).
 */
public final class MiniCdnCliMain {

    /**
     * Startet die CLI.
     *
     * <p>Verhalten:
     * - Mit Argument: führt den Befehl aus und beendet den Prozess mit dem Exit-Code.
     * - Ohne Argument: startet eine REPL/Shell, die Kommandos interaktiv ausführt.
     *
     * @param args Kommandozeilenargumente (kann leer sein)
     * @throws Exception bei Terminal-Initialisierung oder unerwarteten Laufzeitfehlern
     */
    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PrintWriter out = terminal.writer();
        PrintWriter err = terminal.writer();

        CliContext ctx = new CliContext(
                terminal,
                out,
                err,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Duration.ofSeconds(5));

        CommandLine cmd = new CommandLine(MiniCdnRootCommand.class, new ContextFactory(ctx));

        if (args != null && args.length > 0) {
            int rc = cmd.execute(args);
            System.exit(rc);
        }

        new MiniCdnInteractiveShell(cmd, ctx).run();
    }
}
