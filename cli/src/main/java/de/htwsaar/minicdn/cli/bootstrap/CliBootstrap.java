package de.htwsaar.minicdn.cli.bootstrap;

import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;

import de.htwsaar.minicdn.cli.adapter.in.cli.admin.AdminCommand;
import de.htwsaar.minicdn.cli.adapter.in.cli.root.MiniCdnRootCommand;
import de.htwsaar.minicdn.cli.adapter.in.cli.shell.MiniCdnInteractiveShell;
import de.htwsaar.minicdn.cli.adapter.in.cli.system.SystemCommand;
import de.htwsaar.minicdn.cli.adapter.in.cli.user.UserCommand;
import de.htwsaar.minicdn.cli.adapter.out.http.HttpAdminOperations;
import de.htwsaar.minicdn.cli.adapter.out.http.HttpSystemBootstrapGateway;
import de.htwsaar.minicdn.cli.adapter.out.http.HttpUserFileTransfers;
import de.htwsaar.minicdn.cli.adapter.out.http.HttpUserOperations;
import de.htwsaar.minicdn.cli.adapter.out.http.TransportClientFactory;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.application.context.CliContext;
import de.htwsaar.minicdn.cli.application.context.CliSessionState;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import de.htwsaar.minicdn.cli.domain.port.SystemBootstrapGateway;
import de.htwsaar.minicdn.cli.domain.port.UserFileTransfers;
import de.htwsaar.minicdn.cli.domain.port.UserOperations;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/**
 * Bootstrapping-Klasse der Mini-CDN-CLI.
 *
 * <p>Die Klasse kapselt den vollständigen Start der CLI-Infrastruktur und hält
 * damit die eigentliche Main-Klasse bewusst schlank. Sie initialisiert Terminal,
 * Kontext und Transport, konfiguriert Picocli und entscheidet zwischen Batch-
 * Modus und interaktiver Shell.</p>
 *
 * <p>Ziele dieses Bootstraps:
 * <ul>
 *   <li>Terminal und Ausgabekanäle initialisieren</li>
 *   <li>Transport und CLI-Kontext aufbauen</li>
 *   <li>Picocli mit ContextFactory konfigurieren</li>
 *   <li>zwischen Einmal-Ausführung und interaktiver Shell entscheiden</li>
 * </ul>
 */
public final class CliBootstrap {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private CliBootstrap() {}

    /**
     * Führt den vollständigen CLI-Startablauf aus.
     *
     * @param args rohe Kommandozeilenargumente
     * @throws Exception wenn das Terminal oder das Bootstrapping fehlschlägt
     */
    public static void run(String[] args) throws Exception {
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
        AdminOperations adminOperations = new HttpAdminOperations(transportClient, REQUEST_TIMEOUT);
        UserOperations userOperations = new HttpUserOperations(transportClient, REQUEST_TIMEOUT);
        UserFileTransfers userFileTransfers = new HttpUserFileTransfers(transportClient, REQUEST_TIMEOUT);
        SystemBootstrapGateway systemBootstrapGateway = new HttpSystemBootstrapGateway(transportClient);

        return new CliContext(
                terminal,
                out,
                err,
                transportClient,
                REQUEST_TIMEOUT,
                adminToken,
                routerBaseUrl,
                sessionState,
                adminOperations,
                userOperations,
                userFileTransfers,
                systemBootstrapGateway);
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
        return TransportClientFactory.http(CONNECT_TIMEOUT, true);
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
     * Führt ein geparstes Kommando aus und erzwingt Login- bzw. Rollenregeln.
     *
     * @param parseResult Picocli-Parsergebnis
     * @param ctx zentraler CLI-Kontext
     * @param err Writer für Fehlermeldungen
     * @return Exit-Code des Kommandos
     */
    private static int executeWithAdminGuard(CommandLine.ParseResult parseResult, CliContext ctx, PrintWriter err) {

        boolean adminCommandRequested = isAdminCommandRequested(parseResult);
        boolean userCommandRequested = isUserCommandRequested(parseResult);
        boolean systemCommandRequested = isSystemCommandRequested(parseResult);
        boolean helpRequested = parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested();

        if (!helpRequested
                && !systemCommandRequested
                && (adminCommandRequested || userCommandRequested)
                && !ctx.sessionState().isLoggedIn()) {
            err.println("[AUTH] Zugriff verweigert: Bitte zuerst einloggen.");
            err.println("[AUTH] Reihenfolge: 1) system init  2) system login --name <user>  3) user/admin ...");
            err.flush();
            return REJECTED.code();
        }

        if (adminCommandRequested && !helpRequested && !ctx.sessionState().isAdminLoggedIn()) {
            err.println("[AUTH] Zugriff verweigert: Für Admin-Befehle ist ein Login als Admin nötig.");
            err.println("[AUTH] Reihenfolge: 1) system init  2) system login --name <admin>  3) admin ...");
            err.flush();
            return REJECTED.code();
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

    /**
     * Prüft, ob der aktuell geparste Aufruf ein User-Command enthält.
     *
     * @param parseResult Picocli-Parsergebnis
     * @return {@code true}, wenn im Command-Baum ein {@link UserCommand} vorkommt
     */
    private static boolean isUserCommandRequested(CommandLine.ParseResult parseResult) {
        return parseResult.asCommandLineList().stream()
                .anyMatch(commandLine -> commandLine.getCommand() instanceof UserCommand);
    }

    /**
     * Prüft, ob der aktuell geparste Aufruf ein System-Command enthält.
     *
     * @param parseResult Picocli-Parsergebnis
     * @return {@code true}, wenn im Command-Baum ein {@link SystemCommand} vorkommt
     */
    private static boolean isSystemCommandRequested(CommandLine.ParseResult parseResult) {
        return parseResult.asCommandLineList().stream()
                .anyMatch(commandLine -> commandLine.getCommand() instanceof SystemCommand);
    }
}
