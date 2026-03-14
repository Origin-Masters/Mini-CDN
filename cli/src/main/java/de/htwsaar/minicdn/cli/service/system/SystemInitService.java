package de.htwsaar.minicdn.cli.service.system;

import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlogik für den lokalen Bootstrap von Origin/Edge/Router.
 *
 * <p>Der Service prüft zunächst, ob die benötigten Komponenten bereits laufen. Falls nicht,
 * startet er die ausführbaren JARs der einzelnen Module, wartet auf die Öffnung der erwarteten
 * Ports und führt anschließend Router-Healthcheck sowie die Registrierung des Edge-Servers aus.
 */
public final class SystemInitService {
    /** Maximale Wartezeit, bis ein gestarteter Dienst seinen Port geöffnet haben muss. */
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(45);

    /** Standard-Port des Origin-Servers. */
    private static final int ORIGIN_PORT = 8080;

    /** Standard-Port des Edge-Servers. */
    private static final int EDGE_PORT = 8081;

    /** Standard-Port des Routers. */
    private static final int ROUTER_PORT = 8082;

    /** Region, unter der sich der lokale Edge beim Router registriert. */
    private static final String EDGE_REGION = "EU";

    /** Basis-URL des lokalen Origin-Servers fuer den Edge-Start ueber den Router. */
    private static final String ORIGIN_BASE_URL = "http://localhost:" + ORIGIN_PORT;

    /** Verantwortlich für das Starten der Java-Prozesse. */
    private final ServiceLauncher launcher;

    /** HTTP-Transport zum Prüfen des Routers und Registrieren des Edge-Servers. */
    private final TransportClient transportClient;

    /**
     * Erstellt einen neuen Bootstrap-Service.
     *
     * @param launcher startet lokale Service-Prozesse
     * @param transportClient führt HTTP-Anfragen gegen Router-Endpunkte aus
     */
    public SystemInitService(ServiceLauncher launcher, TransportClient transportClient) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
    }

    /**
     * Initialisiert das lokale Mini-CDN-System.
     *
     * <p>Es werden Origin und Router sichergestellt und optional der Edge gestartet. Danach wird
     * geprüft, ob der Router erreichbar ist und der Edge erfolgreich beim Router registriert
     * werden konnte.
     *
     * @param projectDir Projektwurzel, relativ zu der die Modul-JARs gesucht werden
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout für HTTP-Anfragen an den Router
     * @param adminToken optionales Admin-Token für geschützte Router-Endpunkte
     * @param startEdge {@code true}, wenn der Edge ebenfalls gestartet und registriert werden soll
     * @return zusammengefasstes Ergebnis der Initialisierung mit Einzelstatus der Services
     */
    public InitResult init(Path projectDir, URI routerBaseUrl, Duration timeout, String adminToken, boolean startEdge) {
        Objects.requireNonNull(projectDir, "projectDir");
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(timeout, "timeout");

        Path root = projectDir.toAbsolutePath().normalize();

        ServiceStatus origin = ensureRunning(root, "origin", "origin", ORIGIN_PORT, "origin");
        ServiceStatus router = ensureRunning(root, "router", "cdn", ROUTER_PORT, "router");

        if (isFailed(origin) || isFailed(router)) {
            return new InitResult(
                    origin,
                    ServiceStatus.skipped("edge"),
                    router,
                    false,
                    "Mindestens ein Service konnte nicht gestartet werden.");
        }

        if (!router.running()) {
            return new InitResult(origin, ServiceStatus.skipped("edge"), router, false, "Router ist nicht erreichbar.");
        }

        boolean routerHealthy = waitRouterHealthy(routerBaseUrl, timeout, adminToken);
        if (!routerHealthy) {
            return new InitResult(
                    origin, ServiceStatus.skipped("edge"), router, false, "Router-Healthcheck fehlgeschlagen.");
        }

        ServiceStatus edge = startEdge
                ? ensureEdgeRunningAndRegistered(routerBaseUrl, timeout, adminToken)
                : ServiceStatus.skipped("edge");

        if (isFailed(edge)) {
            return new InitResult(origin, edge, router, false, "Mindestens ein Service konnte nicht gestartet werden.");
        }

        return new InitResult(origin, edge, router, true, "System erfolgreich initialisiert.");
    }

    /**
     * Stellt sicher, dass der Edge läuft und beim Router registriert ist.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout für HTTP-Anfragen
     * @param adminToken optionales Admin-Token
     * @return Status des Edge-Services oder Fehlerstatus bei fehlgeschlagener Registrierung
     */
    private ServiceStatus ensureEdgeRunningAndRegistered(URI routerBaseUrl, Duration timeout, String adminToken) {
        TransportResponse response = startManagedEdgeAtRouter(routerBaseUrl, timeout, adminToken);
        if (!response.is2xx()) {
            Integer statusCode = response.statusCode();
            if (statusCode != null && statusCode == 409 && isPortOpen(EDGE_PORT)) {
                return ServiceStatus.failed(
                        "edge",
                        EDGE_PORT,
                        "Edge-Port " + EDGE_PORT
                                + " ist bereits belegt. Starte die bestehende Edge neu ueber '/api/cdn/admin/edges/start', damit sie managed wird.");
            }

            String detail = response.error();
            if (detail == null || detail.isBlank()) {
                detail = "HTTP " + Objects.toString(statusCode, "n/a")
                        + (response.body() == null || response.body().isBlank() ? "" : ": " + response.body());
            }
            return ServiceStatus.failed("edge", EDGE_PORT, "Managed Edge-Start fehlgeschlagen: " + detail);
        }

        return ServiceStatus.started("edge", EDGE_PORT, null);
    }

    /**
     * Stellt sicher, dass ein bestimmter Dienst läuft.
     *
     * <p>Wenn der Port bereits geöffnet ist, wird der Dienst als bereits laufend markiert.
     * Andernfalls wird die JAR des Moduls gestartet und auf die Portfreigabe gewartet.
     *
     * @param root Projektwurzel
     * @param module Modulname, der zugleich für den JAR-Pfad verwendet wird
     * @param profile Laufzeitprofil für den Startvorgang
     * @param port erwarteter Port des Dienstes
     * @param logName Dateiname für das Log ohne Erweiterung
     * @return Status des Dienstes nach Start- bzw. Verfügbarkeitsprüfung
     */
    private ServiceStatus ensureRunning(Path root, String module, String profile, int port, String logName) {
        if (isPortOpen(port)) {
            return ServiceStatus.alreadyRunning(module, port);
        }

        Path jar = root.resolve(module).resolve("target").resolve(module + "-1.0-SNAPSHOT-exec.jar");
        Path log = root.resolve(logName + ".log");

        if (!Files.exists(jar)) {
            return ServiceStatus.failed(module, port, "JAR fehlt: " + jar);
        }

        Process process = launcher.start(jar, profile, log);

        boolean up = waitPort(port, STARTUP_TIMEOUT);
        if (!up) {
            return ServiceStatus.failed(module, port, "Port " + port + " wurde nicht geöffnet (siehe " + log + ")");
        }

        return ServiceStatus.started(module, port, process.pid());
    }

    /**
     * Wartet darauf, dass der Router über seinen Health-Endpunkt erfolgreich antwortet.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout für einzelne HTTP-Anfragen
     * @param adminToken optionales Admin-Token für den Request-Header
     * @return {@code true}, wenn innerhalb der Startfrist ein 2xx-Status empfangen wurde
     */
    private boolean waitRouterHealthy(URI routerBaseUrl, Duration timeout, String adminToken) {
        URI uri = UriUtils.ensureTrailingSlash(routerBaseUrl).resolve("api/cdn/health");
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            TransportResponse response = transportClient.send(TransportRequest.get(
                    uri, timeout, adminToken == null ? Map.of() : Map.of("X-Admin-Token", adminToken)));
            if (response.is2xx()) {
                return true;
            }
            sleepOneSecond();
        }

        return false;
    }

    /**
     * Startet den lokalen Edge ueber den Router-Lifecycle-Endpunkt inklusive Auto-Registrierung.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout fuer den Start-Request
     * @param adminToken optionales Admin-Token für geschützte Router-Endpunkte
     * @return HTTP-Antwort des Router-Requests
     */
    private TransportResponse startManagedEdgeAtRouter(URI routerBaseUrl, Duration timeout, String adminToken) {
        URI uri = UriUtils.ensureTrailingSlash(routerBaseUrl).resolve("api/cdn/admin/edges/start");

        String jsonBody = "{"
                + "\"region\":\"" + JsonUtils.escapeJson(EDGE_REGION) + "\","
                + "\"port\":" + EDGE_PORT + ","
                + "\"originBaseUrl\":\"" + JsonUtils.escapeJson(ORIGIN_BASE_URL) + "\","
                + "\"autoRegister\":true,"
                + "\"waitUntilReady\":true"
                + "}";

        Map<String, String> headers = adminToken == null || adminToken.isBlank()
                ? Map.of("Content-Type", "application/json")
                : Map.of("X-Admin-Token", adminToken, "Content-Type", "application/json");

        return transportClient.send(TransportRequest.postJson(uri, timeout, headers, jsonBody));
    }

    /**
     * Prüft, ob ein Service-Status einen Fehler repräsentiert.
     *
     * @param status zu prüfender Status
     * @return {@code true}, wenn der Statuszustand {@code FAILED} ist
     */
    private boolean isFailed(ServiceStatus status) {
        return "FAILED".equals(status.state());
    }

    /**
     * Wartet darauf, dass ein TCP-Port erreichbar wird.
     *
     * @param port Zielport auf localhost
     * @param timeout maximale Wartezeit
     * @return {@code true}, wenn der Port innerhalb der Frist geöffnet wurde
     */
    private boolean waitPort(int port, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isPortOpen(port)) {
                return true;
            }
            sleepOneSecond();
        }
        return false;
    }

    /**
     * Prüft, ob ein TCP-Port auf {@code 127.0.0.1} erreichbar ist.
     *
     * @param port zu prüfender Port
     * @return {@code true}, wenn eine Verbindung aufgebaut werden kann
     */
    private boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Pausiert den aktuellen Thread für eine Sekunde.
     *
     * <p>Bei Unterbrechung wird das Interrupt-Flag des Threads wieder gesetzt.
     */
    private void sleepOneSecond() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Beschreibt den Zustand eines einzelnen Dienstes während der Initialisierung.
     *
     * @param name Name des Dienstes
     * @param port zugeordneter Port oder {@code -1} bei nicht anwendbar
     * @param state technischer Zustand, z. B. {@code STARTED}, {@code ALREADY_RUNNING},
     *     {@code SKIPPED} oder {@code FAILED}
     * @param message lesbare Kurzbeschreibung des Zustands
     * @param pid Prozess-ID des gestarteten Dienstes, sofern verfügbar
     */
    public record ServiceStatus(String name, int port, String state, String message, Long pid) {
        /**
         * Erzeugt einen Status für einen erfolgreich gestarteten Dienst.
         *
         * @param name Name des Dienstes
         * @param port Port des Dienstes
         * @param pid Prozess-ID des gestarteten Prozesses
         * @return neuer Status mit Zustand {@code STARTED}
         */
        public static ServiceStatus started(String name, int port, Long pid) {
            return new ServiceStatus(name, port, "STARTED", "gestartet", pid);
        }

        /**
         * Erzeugt einen Status für einen Dienst, der bereits läuft.
         *
         * @param name Name des Dienstes
         * @param port Port des Dienstes
         * @return neuer Status mit Zustand {@code ALREADY_RUNNING}
         */
        public static ServiceStatus alreadyRunning(String name, int port) {
            return new ServiceStatus(name, port, "ALREADY_RUNNING", "lief bereits", null);
        }

        /**
         * Erzeugt einen Status für einen übersprungenen Dienst.
         *
         * @param name Name des Dienstes
         * @return neuer Status mit Zustand {@code SKIPPED}
         */
        public static ServiceStatus skipped(String name) {
            return new ServiceStatus(name, -1, "SKIPPED", "übersprungen", null);
        }

        /**
         * Erzeugt einen Fehlerstatus für einen Dienst.
         *
         * @param name Name des Dienstes
         * @param port Port des Dienstes
         * @param message Fehlerbeschreibung
         * @return neuer Status mit Zustand {@code FAILED}
         */
        public static ServiceStatus failed(String name, int port, String message) {
            return new ServiceStatus(name, port, "FAILED", message, null);
        }

        /**
         * Gibt zurück, ob der Dienst als verfügbar betrachtet werden kann.
         *
         * @return {@code true}, wenn der Dienst gestartet wurde oder bereits lief
         */
        public boolean running() {
            return "STARTED".equals(state) || "ALREADY_RUNNING".equals(state);
        }
    }

    /**
     * Gesamtergebnis der Systeminitialisierung.
     *
     * @param origin Status des Origin-Servers
     * @param edge Status des Edge-Servers
     * @param router Status des Routers
     * @param success {@code true}, wenn die Initialisierung erfolgreich abgeschlossen wurde
     * @param message zusammenfassende Meldung zum Ergebnis
     */
    public record InitResult(
            ServiceStatus origin, ServiceStatus edge, ServiceStatus router, boolean success, String message) {}
}
