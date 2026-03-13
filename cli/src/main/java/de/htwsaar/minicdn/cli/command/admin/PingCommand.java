package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ORIGIN_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Admin-Command für einen einfachen Health-Check gegen einen HTTP-Endpunkt.
 *
 * <p>Die Klasse bildet einen sehr kleinen CLI-Adapter. Sie validiert die Eingaben,
 * baut die Ziel-URI auf, führt einen GET-Request über den gemeinsamen
 * {@link de.htwsaar.minicdn.cli.transport.TransportClient} aus und gibt
 * Statuscode sowie Response-Body auf der Konsole aus.</p>
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

    /**
     * Standardpfad für den Health-Check.
     */
    static final String DEFAULT_PATH = "health";

    /**
     * Gemeinsamer CLI-Kontext.
     */
    private final CliContext ctx;

    @Option(
            names = {"-H", "--host"},
            defaultValue = ORIGIN_URL,
            paramLabel = "BASE_URL",
            description = "Base URL, e.g. http://localhost:8080")
    private URI host;

    @Option(
            names = {"-p", "--path"},
            defaultValue = DEFAULT_PATH,
            paramLabel = "PATH",
            description = "Path relative to host, default: ${DEFAULT-VALUE} (no leading slash)")
    private String path;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public PingCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public Integer call() {
        PrintWriter out = ctx.out();
        PrintWriter err = ctx.err();

        try {
            URI base = normalizeHost(host);
            String cleanPath = normalizePath(path);
            URI url = base.resolve(cleanPath);

            CallResult response = TransportCallAdapter.execute(
                    ctx.transportClient(), TransportRequest.get(url, ctx.defaultRequestTimeout(), Map.of()));

            if (response.error() != null) {
                return requestFailed(err, "Ping failed: %s", response.error());
            }

            Integer statusCode = response.statusCode();
            if (statusCode == null) {
                return requestFailed(err, "Ping failed: missing HTTP status code");
            }

            printResponse(out, statusCode, response.body());
            return response.is2xx() ? SUCCESS.code() : REJECTED.code();

        } catch (IllegalArgumentException ex) {
            return rejected(err, ex.getMessage());
        } catch (Exception ex) {
            return requestFailed(err, "Ping failed: %s", ex.getMessage());
        }
    }

    /**
     * Normalisiert und validiert die Basis-URL.
     *
     * @param rawHost rohe Host-URI
     * @return normalisierte Basis-URI mit Trailing Slash
     * @throws IllegalArgumentException falls die URI ungültig ist
     */
    URI normalizeHost(URI rawHost) {
        URI value = Objects.requireNonNull(rawHost, "host");

        if (!value.isAbsolute() || value.getScheme() == null) {
            throw new IllegalArgumentException("host must be an absolute http/https URI");
        }

        String scheme = value.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("host must use http or https");
        }

        return UriUtils.ensureTrailingSlash(value);
    }

    /**
     * Normalisiert und validiert den relativen Zielpfad.
     *
     * @param rawPath roher CLI-Pfad
     * @return bereinigter relativer Pfad ohne führenden Slash
     * @throws IllegalArgumentException falls der Pfad leer oder ungültig ist
     */
    String normalizePath(String rawPath) {
        String value = PathUtils.stripLeadingSlash(Objects.toString(rawPath, "").trim());
        if (value.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        return value;
    }

    /**
     * Gibt die Response für den Health-Check aus.
     *
     * @param out Standardausgabe
     * @param statusCode HTTP-Statuscode
     * @param body Response-Body
     */
    void printResponse(PrintWriter out, int statusCode, String body) {
        out.println("Status: " + statusCode);

        String responseBody = Objects.toString(body, "");
        if (!responseBody.isBlank()) {
            out.println(responseBody);
        }

        out.flush();
    }

    /**
     * Gibt einen technischen Fehler einheitlich aus.
     *
     * @param err Fehlerausgabe
     * @param pattern Formatmuster
     * @param args Formatargumente
     * @return Exit-Code für technische Fehler
     */
    int requestFailed(PrintWriter err, String pattern, Object... args) {
        ConsoleUtils.error(err, "[ADMIN] " + pattern, args);
        return REQUEST_FAILED.code();
    }

    /**
     * Gibt einen Validierungsfehler oder eine fachliche Ablehnung einheitlich aus.
     *
     * @param err Fehlerausgabe
     * @param message Fehlermeldung
     * @return Exit-Code für fachliche Ablehnung
     */
    int rejected(PrintWriter err, String message) {
        ConsoleUtils.error(err, "[ADMIN] %s", Objects.toString(message, "request rejected"));
        return REJECTED.code();
    }
}
