package de.htwsaar.minicdn.cli.di;

import de.htwsaar.minicdn.cli.transport.TransportClient;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Scanner;
import org.jline.terminal.Terminal;

/**
 * Gemeinsamer Laufzeit-Kontext für CLI-Commands.
 *
 * <p>Aufgaben:
 * - Bündelt Terminal und Ausgabekanäle (stdout/stderr).
 * - Stellt Shared-Infrastruktur bereit (z. B. Transport-Adapter, Default-Timeout).
 * - Ermöglicht testbare Commands durch Constructor Injection statt statischer Globals.
 *
 * <p>Konvention:
 * - Hier gehören nur generische Abhängigkeiten hinein (I/O, Transport, Timeouts),
 *   keine fachlichen Services.
 */
public final class CliContext {
    private final Terminal terminal;
    private final PrintWriter out;
    private final PrintWriter err;
    private final TransportClient transportClient;
    private final Duration defaultRequestTimeout;
    private final String adminToken;
    private final URI routerBaseUrl;
    private final CliSessionState sessionState;

    /**
     * Erzeugt einen neuen CLI-Kontext.
     *
     * @param terminal JLine-Terminal für interaktive Features
     * @param out Writer für normale Ausgaben
     * @param err Writer für Fehlermeldungen
     * @param transportClient gemeinsame Transport-Abstraktion für API-Aufrufe
     * @param defaultRequestTimeout Standard-Timeout für Requests
     * @param adminToken Token für Admin-/geschützte Aufrufe
     * @param routerBaseUrl Router-Basis-URL
     * @param sessionState Laufzeitstatus für in dieser Session gestartete Ressourcen
     */
    public CliContext(
            Terminal terminal,
            PrintWriter out,
            PrintWriter err,
            TransportClient transportClient,
            Duration defaultRequestTimeout,
            String adminToken,
            URI routerBaseUrl,
            CliSessionState sessionState) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.defaultRequestTimeout = Objects.requireNonNull(defaultRequestTimeout, "defaultRequestTimeout");
        this.adminToken = Objects.requireNonNull(adminToken, "adminToken");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
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

    public TransportClient transportClient() {
        return transportClient;
    }

    public Duration defaultRequestTimeout() {
        return defaultRequestTimeout;
    }

    public Scanner in() {
        return new Scanner(terminal.input());
    }

    public String adminToken() {
        return adminToken;
    }

    public URI routerBaseUrl() {
        return routerBaseUrl;
    }

    public CliSessionState sessionState() {
        return sessionState;
    }
}
