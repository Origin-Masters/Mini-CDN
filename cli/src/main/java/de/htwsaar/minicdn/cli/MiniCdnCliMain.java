package de.htwsaar.minicdn.cli.app;

import de.htwsaar.minicdn.cli.command.root.MiniCdnRootCommand;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.di.ContextFactory;
import de.htwsaar.minicdn.cli.shell.MiniCdnInteractiveShell;
import de.htwsaar.minicdn.cli.transport.HttpTransportClient;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/**
 * Einstiegspunkt der Mini-CDN CLI-Anwendung.
 *
 * Aufgaben:
 * <ul>
 *   <li>Initialisiert Terminal-IO (JLine) sowie gemeinsame Infrastruktur (TransportClient, Standard-Timeouts).</li>
 *   <li>Baut die Picocli-Command-Struktur inkl. {@link ContextFactory} für Constructor Injection.</li>
 *   <li>Wählt den Modus: einmalige Ausführung (Args vorhanden) oder interaktive Shell (keine Args).</li>
 * </ul>
 */
public final class MiniCdnCliMain {

    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PrintWriter out = terminal.writer();
        PrintWriter err = terminal.writer();

        String adminToken = resolveAdminToken();
        URI routerUrl = resolveRouterBaseUrl();

        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        Duration timeout = Duration.ofSeconds(5);
        TransportClient transportClient = new HttpTransportClient(httpClient);

        CliContext ctx = new CliContext(terminal, out, err, transportClient, timeout, adminToken, routerUrl);

        CommandLine cmd = new CommandLine(MiniCdnRootCommand.class, new ContextFactory(ctx));

        if (args != null && args.length > 0) {
            int rc = cmd.execute(args);
            System.exit(rc);
        }

        new MiniCdnInteractiveShell(cmd, ctx).run();
    }

    /**
     * Admin-Token:
     * 1) System-Property: -Dminicdn.admin.token=...
     * 2) ENV: MINICDNADMINTOKEN
     * 3) Fallback: "secret-token"
     */
    private static String resolveAdminToken() {
        String fromProp = System.getProperty("minicdn.admin.token");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv("MINICDNADMINTOKEN");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return "secret-token";
    }

    /**
     * Router-Base-URL:
     * 1) System-Property: -Dminicdn.router.url=...
     * 2) ENV: MINICDN_ROUTER_URL
     * 3) Fallback: "http://localhost:8082"
     */
    private static URI resolveRouterBaseUrl() {
        String raw = System.getProperty("minicdn.router.url");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("MINICDN_ROUTER_URL");
        }
        if (raw == null || raw.isBlank()) {
            raw = "http://localhost:8082";
        }
        return URI.create(raw.trim());
    }
}
