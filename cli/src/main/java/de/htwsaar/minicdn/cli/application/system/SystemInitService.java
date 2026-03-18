package de.htwsaar.minicdn.cli.application.system;

import de.htwsaar.minicdn.cli.domain.model.ManagedEdgeStartResult;
import de.htwsaar.minicdn.cli.domain.port.SystemBootstrapGateway;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Fachlogik für den lokalen Bootstrap von Origin, Edge und Router.
 *
 * <p>Lokale Prozessstarts und Portprüfungen bleiben hier. Remote-Aufrufe an den
 * Router werden über einen fachlichen Port gekapselt.</p>
 */
public final class SystemInitService {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final int ORIGIN_PORT = 8080;
    private static final int EDGE_PORT = 8081;
    private static final int ROUTER_PORT = 8082;
    private static final String EDGE_REGION = "EU";
    private static final String ORIGIN_BASE_URL = "http://localhost:" + ORIGIN_PORT;

    private final ServiceLauncher launcher;
    private final SystemBootstrapGateway systemBootstrapGateway;

    /**
     * Erstellt einen neuen Bootstrap-Service.
     *
     * @param launcher startet lokale Service-Prozesse
     * @param systemBootstrapGateway fachlicher Port für bootstrap-relevante Remote-Aufrufe
     */
    public SystemInitService(ServiceLauncher launcher, SystemBootstrapGateway systemBootstrapGateway) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.systemBootstrapGateway = Objects.requireNonNull(systemBootstrapGateway, "systemBootstrapGateway");
    }

    /**
     * Initialisiert das lokale Mini-CDN-System.
     *
     * @param projectDir Projektwurzel, relativ zu der die Modul-JARs gesucht werden
     * @param routerBaseUrl Basis-URL des Routers
     * @param timeout Timeout für Router-Aufrufe
     * @param adminToken optionales Admin-Token für geschützte Router-Operationen
     * @param startEdge {@code true}, wenn der Edge ebenfalls gestartet und registriert werden soll
     * @return zusammengefasstes Ergebnis der Initialisierung
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

        if (!waitRouterHealthy(routerBaseUrl, timeout, adminToken)) {
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

    private ServiceStatus ensureEdgeRunningAndRegistered(URI routerBaseUrl, Duration timeout, String adminToken) {
        ManagedEdgeStartResult result = systemBootstrapGateway.startManagedEdge(
                routerBaseUrl, timeout, adminToken, EDGE_REGION, EDGE_PORT, ORIGIN_BASE_URL);

        if (result.status() == ManagedEdgeStartResult.Status.STARTED) {
            return ServiceStatus.started("edge", EDGE_PORT, null);
        }

        if (result.status() == ManagedEdgeStartResult.Status.CONFLICT && isPortOpen(EDGE_PORT)) {
            return ServiceStatus.failed(
                    "edge",
                    EDGE_PORT,
                    "Edge-Port " + EDGE_PORT
                            + " ist bereits belegt. Starte die bestehende Edge neu über '/api/cdn/admin/edges/activations', damit sie managed wird.");
        }

        String detail = result.message();
        if (detail == null || detail.isBlank()) {
            detail = "Managed Edge-Start fehlgeschlagen.";
        }
        return ServiceStatus.failed("edge", EDGE_PORT, "Managed Edge-Start fehlgeschlagen: " + detail);
    }

    private ServiceStatus ensureRunning(Path root, String module, String profile, int port, String logName) {
        if (isPortOpen(port)) {
            return ServiceStatus.alreadyRunning(module, port);
        }

        Path jar = resolveModuleJar(root, module);
        if (jar == null) {
            return ServiceStatus.failed(
                    module,
                    port,
                    "JAR nicht gefunden: "
                            + root.resolve(module).resolve("target").resolve(module + "-<version>-exec.jar"));
        }

        Path logFile = root.resolve(module + "-" + logName + ".log");
        try {
            Process process = launcher.start(jar, profile, logFile);
            if (!waitPort(port, STARTUP_TIMEOUT)) {
                return ServiceStatus.failed(module, port, "Dienst hat Port " + port + " nicht rechtzeitig geöffnet.");
            }
            return ServiceStatus.started(module, port, process.pid());
        } catch (Exception ex) {
            return ServiceStatus.failed(module, port, ex.getMessage());
        }
    }

    private Path resolveModuleJar(Path root, String module) {
        Path targetDir = root.resolve(module).resolve("target");
        if (!Files.isDirectory(targetDir)) {
            return null;
        }

        try (Stream<Path> jars = Files.list(targetDir)) {
            return jars.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(module + "-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
                    .filter(path -> !path.getFileName().toString().endsWith("-javadoc.jar"))
                    .sorted(Comparator.comparing((Path path) ->
                                    !path.getFileName().toString().endsWith("-exec.jar"))
                            .thenComparing(path -> path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private boolean waitRouterHealthy(URI routerBaseUrl, Duration timeout, String adminToken) {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (systemBootstrapGateway.isRouterHealthy(routerBaseUrl, timeout, adminToken)) {
                return true;
            }
            sleepOneSecond();
        }
        return false;
    }

    private boolean isFailed(ServiceStatus status) {
        return "FAILED".equals(status.state());
    }

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

    private boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

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
     * @param state technischer Zustand, z. B. {@code STARTED}, {@code ALREADY_RUNNING}, {@code SKIPPED} oder {@code FAILED}
     * @param message lesbare Kurzbeschreibung des Zustands
     * @param pid Prozess-ID des gestarteten Dienstes, sofern verfügbar
     */
    public record ServiceStatus(String name, int port, String state, String message, Long pid) {

        public static ServiceStatus started(String name, int port, Long pid) {
            return new ServiceStatus(name, port, "STARTED", "gestartet", pid);
        }

        public static ServiceStatus alreadyRunning(String name, int port) {
            return new ServiceStatus(name, port, "ALREADY_RUNNING", "lief bereits", null);
        }

        public static ServiceStatus skipped(String name) {
            return new ServiceStatus(name, -1, "SKIPPED", "übersprungen", null);
        }

        public static ServiceStatus failed(String name, int port, String message) {
            return new ServiceStatus(name, port, "FAILED", message, null);
        }

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
