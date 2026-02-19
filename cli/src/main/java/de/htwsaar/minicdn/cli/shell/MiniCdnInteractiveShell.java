package de.htwsaar.minicdn.cli.shell;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

/**
 * Interaktive Shell (REPL) für die Mini-CDN CLI.
 *
 * <p>Aufgaben:
 * - Liest Benutzereingaben zeilenweise und führt sie als Picocli-Kommandos aus.
 * - Bietet Autovervollständigung über {@link PicocliCommands} + JLine.
 * - Verwaltet Shell-spezifische Befehle (z. B. {@code clear}, {@code exit}) und History.
 *
 * <p>Beenden:
 * - Ctrl+C: aktuelle Eingabe verwerfen.
 * - Ctrl+D oder {@code exit}/{@code quit}: Shell beenden.
 */
public final class MiniCdnInteractiveShell {
    private final CommandLine cmd;
    private final CliContext ctx;

    /**
     * @param cmd vorkonfiguriertes Picocli-Root-CommandLine-Objekt
     * @param ctx CLI-Kontext (Terminal + I/O)
     */
    public MiniCdnInteractiveShell(CommandLine cmd, CliContext ctx) {
        this.cmd = Objects.requireNonNull(cmd, "cmd");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /**
     * Startet die Shell-Schleife und blockiert bis zum Exit.
     *
     * <p>History wird in {@code .minicdn.history} im aktuellen Arbeitsverzeichnis gespeichert.
     */
    public void run() {
        Terminal terminal = ctx.terminal();
        PrintWriter out = ctx.out();
        PrintWriter err = ctx.err();

        PicocliCommands picocli = new PicocliCommands(cmd);

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(picocli.compileCompleters())
                .parser(new DefaultParser())
                .build();

        reader.setVariable(LineReader.HISTORY_FILE, Path.of(".minicdn.history"));

        out.println("Mini-CDN Shell. Type 'help', 'exit', 'clear'.");
        out.flush();

        while (true) {
            final String line;
            try {
                String prompt = new AttributedStringBuilder()
                        .style(AttributedStyle.DEFAULT
                                .foreground(AttributedStyle.GREEN)
                                .bold())
                        .append("mini-cdn> ")
                        .toAnsi();

                line = reader.readLine(prompt).trim();
            } catch (UserInterruptException e) {
                continue; // Ctrl+C
            } catch (EndOfFileException e) {
                break; // Ctrl+D
            }

            if (line.isBlank()) {
                continue;
            }
            if (equalsAnyIgnoreCase(line, "exit", "quit")) {
                break;
            }

            if (equalsAnyIgnoreCase(line, "clear", "cls")) {
                terminal.puts(InfoCmp.Capability.clear_screen);
                terminal.flush();
                continue;
            }

            try {
                ParsedLine parsed = reader.getParser().parse(line, 0);
                List<String> words = parsed.words();
                String[] argv = words.toArray(new String[0]);

                int exitCode = cmd.execute(argv);
                if (exitCode != 0) {
                    err.println("Command failed with exit code: " + exitCode);
                    err.flush();
                }
            } catch (Exception ex) {
                err.println("Error executing command: " + ex.getMessage());
                err.flush();
            }
        }
    }

    private static boolean equalsAnyIgnoreCase(String input, String... candidates) {
        for (String c : candidates) {
            if (input.equalsIgnoreCase(c)) return true;
        }
        return false;
    }
}
