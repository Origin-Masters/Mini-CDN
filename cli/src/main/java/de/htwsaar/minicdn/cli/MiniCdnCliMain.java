package de.htwsaar.minicdn.cli;

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
 * <p>
 * Aufgaben:
 * <ul>
 *   <li>Initialisiert Terminal-IO (Jline) sowie gemeinsame Infrastruktur (TransportClient, Standard-Timeouts).</li>
 *   <li>Baut die Picocli-Command-Struktur inkl. {@link ContextFactory} für Constructor Injection.</li>
 *   <li>Wählt den Modus: einmalige Ausführung (Args vorhanden) oder interaktive Shell (keine Args).</li>
 * </ul>
 */
public final class MiniCdnCliMain {
    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PrintWriter out = terminal.writer();
        PrintWriter err = terminal.writer();

        // Lade Umgebungsvariablen aus .env Datei
        Dotenv dotenv = Dotenv.configure().directory(".").ignoreIfMissing().load();

        String adminToken = dotenv.get("MINICDN_ADMIN_TOKEN");

        String routerBaseUrlStr = dotenv.get("MINICDN_ROUTER_URL");
        URI routerBaseUrl = URI.create(Objects.requireNonNull(routerBaseUrlStr));

        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        Duration timeout = Duration.ofSeconds(5);
        TransportClient transportClient = new HttpTransportClient(httpClient);
        CliSessionState sessionState = new CliSessionState();

        CliContext ctx =
                new CliContext(terminal, out, err, transportClient, timeout, adminToken, routerBaseUrl, sessionState);

        CommandLine cmd = new CommandLine(MiniCdnRootCommand.class, new ContextFactory(ctx));

        if (args != null && args.length > 0) {
            int rc = cmd.execute(args);
            System.exit(rc);
        }

        new MiniCdnInteractiveShell(cmd, ctx).run();
    }
}
