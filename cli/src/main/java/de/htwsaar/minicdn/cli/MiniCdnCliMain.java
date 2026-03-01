package de.htwsaar.minicdn.cli.app;

import de.htwsaar.minicdn.cli.command.root.MiniCdnRootCommand;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.di.ContextFactory;
import de.htwsaar.minicdn.cli.shell.MiniCdnInteractiveShell;
import de.htwsaar.minicdn.cli.transport.HttpTransportClient;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/**
 * Einstiegspunkt der Mini-CDN CLI-Anwendung.
 */
public final class MiniCdnCliMain {

    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PrintWriter out = terminal.writer();
        PrintWriter err = terminal.writer();

        URI routerUrl = URI.create(
                System.getenv("MINICDN_ROUTER_URL") != null
                        ? System.getenv("MINICDN_ROUTER_URL")
                        : "http://localhost:8082");

        String token = System.getenv("MINICDNADMINTOKEN") != null ? System.getenv("MINICDNADMINTOKEN") : "secret-token";

        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        CliContext ctx = new CliContext(
                terminal, out, err, new HttpTransportClient(httpClient), Duration.ofSeconds(5), token, routerUrl);

        CommandLine cmd = new CommandLine(MiniCdnRootCommand.class, new ContextFactory(ctx));

        if (args != null && args.length > 0) {
            int rc = cmd.execute(args);
            System.exit(rc);
        }

        new MiniCdnInteractiveShell(cmd, ctx).run();
    }
}
