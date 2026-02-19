package de.htwsaar.minicdn.cli.di;

import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.jline.terminal.Terminal;

/**
 * Gemeinsamer Laufzeit-Kontext für CLI-Commands.
 *
 * <p>Aufgaben:
 * - Bündelt Terminal und Ausgabekanäle (stdout/stderr).
 * - Stellt Shared-Infrastruktur bereit (z. B. HTTP-Client, Default-Timeout).
 * - Ermöglicht testbare Commands durch Constructor Injection statt statischer Globals.
 *
 * <p>Konvention:
 * - Hier gehören nur generische Abhängigkeiten hinein (I/O, HTTP, Timeouts),
 *   keine fachlichen Services.
 */
public final class CliContext {
    private final Terminal terminal;
    private final PrintWriter out;
    private final PrintWriter err;
    private final HttpClient httpClient;
    private final Duration defaultRequestTimeout;

    /**
     * Erzeugt einen neuen CLI-Kontext.
     *
     * @param terminal JLine-Terminal für interaktive Features (Prompt, Clear, History)
     * @param out Writer für normale Ausgaben
     * @param err Writer für Fehlermeldungen
     * @param httpClient gemeinsamer HTTP-Client für API-Aufrufe
     * @param defaultRequestTimeout Standard-Timeout für HTTP-Requests
     */
    public CliContext(
            Terminal terminal,
            PrintWriter out,
            PrintWriter err,
            HttpClient httpClient,
            Duration defaultRequestTimeout) {
        this.terminal = Objects.requireNonNull(terminal);
        this.out = Objects.requireNonNull(out);
        this.err = Objects.requireNonNull(err);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.defaultRequestTimeout = Objects.requireNonNull(defaultRequestTimeout);
    }

    public Terminal terminal() {
        return terminal;
    }

    public PrintWriter out() {
        return out;
    }

    public PrintWriter err() {
        return err;
    }

    public HttpClient httpClient() {
        return httpClient;
    }

    public Duration defaultRequestTimeout() {
        return defaultRequestTimeout;
    }
}
