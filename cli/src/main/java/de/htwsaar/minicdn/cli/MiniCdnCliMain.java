package de.htwsaar.minicdn.cli;

import de.htwsaar.minicdn.cli.command.admin.AdminCommand;
import de.htwsaar.minicdn.cli.command.root.MiniCdnRootCommand;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.di.CliSessionState;
import de.htwsaar.minicdn.cli.di.ContextFactory;
import de.htwsaar.minicdn.cli.shell.MiniCdnInteractiveShell;
import de.htwsaar.minicdn.cli.transport.HttpTransportClient;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/**
 * Einstiegspunkt der Mini-CDN CLI-Anwendung.
 *
 * <p>Die Klasse bootstrapped die gemeinsame CLI-Infrastruktur und delegiert die
 * eigentliche Fachlogik an vorhandene Commands und Services.
 *
 * <p>Ziele dieses Entrypoints:
 * <ul>
 *   <li>Terminal und Ausgabekanäle initialisieren</li>
 *   <li>Transport und CLI-Kontext aufbauen</li>
 *   <li>Picocli mit ContextFactory konfigurieren</li>
 *   <li>zwischen Einmal-Ausführung und interaktiver Shell entscheiden</li>
 * </ul>
 */
public final class MiniCdnCliMain {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private MiniCdnCliMain() {}

    public static void main(String[] args) throws Exception {
        Terminal terminal = createTerminal();
        PrintWriter out = terminal.writer();
        PrintWriter err = terminal.writer();

        CliContext ctx = createCliContext(terminal, out, err);
        CommandLine cmd = createCommandLine(ctx, err);

        if (hasArgs(args)) {
            int rc = cmd.execute(args);
            System.exit(rc);
            return;
        }

        startInteractiveShell(cmd, ctx);
    }

    /**
     * Erstellt das JLine-Terminal für Batch- und Shell-Modus.
     *
     * @return initialisiertes System-Terminal
     * @throws Exception wenn das Terminal nicht aufgebaut werden kann
     */
    private static Terminal createTerminal() throws Exception {
        return TerminalBuilder.builder().system(true).build();
    }

    /**
     * Baut den zentralen CLI-Kontext aus Terminal, Konfiguration und Transport.
     *
     * @param terminal aktives JLine-Terminal
     * @param out Writer für Standardausgaben
     * @param err Writer für Fehlerausgaben
     * @return vollständig initialisierter CLI-Kontext
     */
    private static CliContext createCliContext(Terminal terminal, PrintWriter out, PrintWriter err) {
        Dotenv dotenv = loadDotenv();

        String adminToken = dotenv.get("MINICDN_ADMIN_TOKEN");
        String routerBaseUrlStr =
                Objects.requireNonNull(dotenv.get("MINICDN_ROUTER_URL"), "MINICDN_ROUTER_URL must be set");

        URI routerBaseUrl = URI.create(routerBaseUrlStr);
        TransportClient transportClient = createTransportClient();
        CliSessionState sessionState = new CliSessionState();

        return new CliContext(
                terminal, out, err, transportClient, REQUEST_TIMEOUT, adminToken, routerBaseUrl, sessionState);
    }

    /**
     * Lädt Umgebungsvariablen aus einer optionalen {@code .env}-Datei im Arbeitsverzeichnis.
     *
     * @return geladene Dotenv-Konfiguration
     */
    private static Dotenv loadDotenv() {
        return Dotenv.configure().directory(".").ignoreIfMissing().load();
    }

    /**
     * Erstellt den konkreten Transport-Adapter für Remote-Aufrufe.
     *
     * @return transportneutrales Client-Interface auf Basis des vorhandenen HTTP-Adapters
     */
    private static TransportClient createTransportClient() {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

        return new HttpTransportClient(httpClient);
    }

    /**
     * Konfiguriert die Picocli-CommandLine inklusive Constructor Injection und Admin-Guard.
     *
     * @param ctx zentraler CLI-Kontext
     * @param err Writer für Fehlermeldungen
     * @return vorkonfigurierte CommandLine
     */
    private static CommandLine createCommandLine(CliContext ctx, PrintWriter err) {
        CommandLine cmd = new CommandLine(MiniCdnRootCommand.class, new ContextFactory(ctx));
        cmd.setExecutionStrategy(parseResult -> executeWithAdminGuard(parseResult, ctx, err));
        return cmd;
    }

    /**
     * Führt ein geparstes Kommando aus und blockiert Admin-Kommandos ohne Admin-Login.
     *
     * @param parseResult Picocli-Parsergebnis
     * @param ctx zentraler CLI-Kontext
     * @param err Writer für Fehlermeldungen
     * @return Exit-Code des Kommandos
     */
    private static int executeWithAdminGuard(CommandLine.ParseResult parseResult, CliContext ctx, PrintWriter err) {

        boolean adminCommandRequested = isAdminCommandRequested(parseResult);
        boolean helpRequested = parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested();

        if (adminCommandRequested && !helpRequested && !ctx.sessionState().isAdminLoggedIn()) {
            err.println("[AUTH] Zugriff verweigert: Für Admin-Befehle ist ein Login als Admin nötig.");
            err.println("[AUTH] Reihenfolge: 1) system init  2) user login --name <admin>  3) admin ...");
            err.flush();
            return 1;
        }

        return new CommandLine.RunLast().execute(parseResult);
    }

    /**
     * Prüft, ob Argumente für den Batch-Modus übergeben wurden.
     *
     * @param args rohe CLI-Argumente
     * @return {@code true}, wenn mindestens ein Argument vorhanden ist
     */
    private static boolean hasArgs(String[] args) {
        return args != null && args.length > 0;
    }

    /**
     * Startet die interaktive Shell auf Basis des bereits konfigurierten Command-Baums.
     *
     * @param cmd vorkonfigurierte CommandLine
     * @param ctx zentraler CLI-Kontext
     */
    private static void startInteractiveShell(CommandLine cmd, CliContext ctx) {
        new MiniCdnInteractiveShell(cmd, ctx).run();
    }

    /**
     * Prüft, ob der aktuell geparste Aufruf ein Admin-Command enthält.
     *
     * @param parseResult Picocli-Parsergebnis
     * @return {@code true}, wenn im Command-Baum ein {@link AdminCommand} vorkommt
     */
    private static boolean isAdminCommandRequested(CommandLine.ParseResult parseResult) {
        return parseResult.asCommandLineList().stream()
                .anyMatch(commandLine -> commandLine.getCommand() instanceof AdminCommand);
    }
}
