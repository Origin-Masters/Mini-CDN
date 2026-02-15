package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "ping", description = "Health check", mixinStandardHelpOptions = true)
public final class PingCommand implements Callable<Integer> {
    private final CliContext ctx;

    public PingCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Option(
            names = {"-h", "--host"},
            defaultValue = "http://localhost:8080",
            description = "Base URL, e.g. http://localhost:8080/api/origin")
    URI host;

    @Option(
            names = {"-p", "--path"},
            defaultValue = "health",
            description = "Path relative to host, default: health (no leading slash)")
    String path;

    @Override
    public Integer call() throws Exception {
        URI base = ensureTrailingSlash(host);
        URI url = base.resolve(stripLeadingSlash(path));

        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(ctx.defaultRequestTimeout())
                .GET()
                .build();

        HttpResponse<String> resp = ctx.httpClient().send(req, HttpResponse.BodyHandlers.ofString());
        ctx.out().println("Status: " + resp.statusCode());
        ctx.out().println(resp.body());

        return resp.statusCode() >= 200 && resp.statusCode() < 300 ? 0 : 2;
    }

    private static URI ensureTrailingSlash(URI uri) {
        String s = uri.toString();
        return URI.create(s.endsWith("/") ? s : s + "/");
    }

    private static String stripLeadingSlash(String p) {
        if (p == null || p.isBlank()) return "";
        return p.startsWith("/") ? p.substring(1) : p;
    }
}
