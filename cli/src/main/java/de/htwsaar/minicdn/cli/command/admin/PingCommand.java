package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Admin-Command für einen einfachen Health-Check gegen einen HTTP-Endpunkt.
 *
 * <p>Exit-Codes:
 * - 0: HTTP 2xx
 * - 2: HTTP non-2xx
 * - 1: Exception/Netzwerkfehler
 */
@Command(
        name = "ping",
        description = "Health check",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin ping",
            "  admin ping -H http://localhost:8082 -p api/cdn/health",
            "  admin ping -H http://localhost:8080 -p api/origin/health"
        })
public final class PingCommand implements Callable<Integer> {

    private final CliContext ctx;

    public PingCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Option(
            names = {"-H", "--host"},
            defaultValue = "http://localhost:8080",
            paramLabel = "BASE_URL",
            description = "Base URL, e.g. http://localhost:8080/api/origin")
    private URI host;

    @Option(
            names = {"-p", "--path"},
            defaultValue = "health",
            paramLabel = "PATH",
            description = "Path relative to host, default: health (no leading slash)")
    private String path;

    @Override
    public Integer call() {
        PrintWriter out = ctx.out();
        PrintWriter err = ctx.err();

        try {
            URI base = UriUtils.ensureTrailingSlash(Objects.requireNonNull(host, "host"));
            URI url = base.resolve(PathUtils.stripLeadingSlash(path));

            HttpRequest req = HttpRequest.newBuilder(url)
                    .timeout(ctx.defaultRequestTimeout())
                    .GET()
                    .build();

            HttpResponse<String> resp = ctx.httpClient().send(req, HttpResponse.BodyHandlers.ofString());

            out.println("Status: " + resp.statusCode());
            out.println(resp.body());
            out.flush();

            return (resp.statusCode() >= 200 && resp.statusCode() < 300) ? 0 : 2;
        } catch (Exception ex) {
            err.println("[ADMIN] Ping failed: " + ex.getMessage());
            err.flush();
            return 1;
        }
    }
}
