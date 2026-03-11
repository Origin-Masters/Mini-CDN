package de.htwsaar.minicdn.cli.service.system;

import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.UriUtils;
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
 */
public final class SystemInitService {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final int ORIGIN_PORT = 8080;
    private static final int EDGE_PORT = 8081;
    private static final int ROUTER_PORT = 8082;
    private static final String EDGE_URL = "http://localhost:8081";

    private final ServiceLauncher launcher;
    private final TransportClient transportClient;

    public SystemInitService(ServiceLauncher launcher, TransportClient transportClient) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
    }

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

        ServiceStatus edge =
                startEdge ? ensureManagedEdge(routerBaseUrl, timeout, adminToken) : ServiceStatus.skipped("edge");

        if (isFailed(edge)) {
            return new InitResult(origin, edge, router, false, "Mindestens ein Service konnte nicht gestartet werden.");
        }

        return new InitResult(origin, edge, router, true, "System erfolgreich initialisiert.");
    }

    private ServiceStatus ensureRunning(Path root, String module, String profile, int port, String logName) {
        if (isPortOpen(port)) {
            return ServiceStatus.alreadyRunning(module, port);
        }

        Path jar = root.resolve(module).resolve("target").resolve(module + "-1.0-SNAPSHOT-exec.jar");
        Path log = root.resolve(logName + ".log");

        if (!Files.exists(jar)) {
            return ServiceStatus.failed(module, port, "JAR fehlt: " + jar);
        }

        launcher.start(jar, profile, log);

        boolean up = waitPort(port, STARTUP_TIMEOUT);
        if (!up) {
            return ServiceStatus.failed(module, port, "Port " + port + " wurde nicht geöffnet (siehe " + log + ")");
        }

        return ServiceStatus.started(module, port);
    }

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

    private ServiceStatus ensureManagedEdge(URI routerBaseUrl, Duration timeout, String adminToken) {
        if (isManagedEdgePresent(routerBaseUrl, timeout, adminToken)) {
            return ServiceStatus.alreadyRunning("edge", EDGE_PORT);
        }

        URI uri = UriUtils.ensureTrailingSlash(routerBaseUrl).resolve("api/cdn/admin/edges/start");
        String json = "{"
                + "\"region\":\"EU\","
                + "\"port\":" + EDGE_PORT + ","
                + "\"originBaseUrl\":\"http://localhost:" + ORIGIN_PORT + "\","
                + "\"autoRegister\":true,"
                + "\"waitUntilReady\":true"
                + "}";

        Map<String, String> headers = adminToken == null || adminToken.isBlank()
                ? Map.of("Content-Type", "application/json")
                : Map.of("X-Admin-Token", adminToken, "Content-Type", "application/json");

        TransportResponse response = transportClient.send(TransportRequest.postJson(uri, timeout, headers, json));

        if (response.is2xx()) {
            return ServiceStatus.started("edge", EDGE_PORT);
        }

        if (isManagedEdgePresent(routerBaseUrl, timeout, adminToken)) {
            return ServiceStatus.alreadyRunning("edge", EDGE_PORT);
        }

        return ServiceStatus.failed(
                "edge",
                EDGE_PORT,
                "Managed-Start fehlgeschlagen (status="
                        + response.statusCode()
                        + ", error="
                        + response.error()
                        + ", body="
                        + response.body()
                        + ")");
    }

    private boolean isManagedEdgePresent(URI routerBaseUrl, Duration timeout, String adminToken) {
        URI uri = UriUtils.ensureTrailingSlash(routerBaseUrl).resolve("api/cdn/admin/edges/managed");
        Map<String, String> headers =
                adminToken == null || adminToken.isBlank() ? Map.of() : Map.of("X-Admin-Token", adminToken);

        TransportResponse response = transportClient.send(TransportRequest.get(uri, timeout, headers));
        if (!response.is2xx() || response.body() == null) {
            return false;
        }

        String body = response.body();
        return body.contains("\"url\":\"" + EDGE_URL + "\"") || body.contains("\"url\":\"" + EDGE_URL + "/\"");
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

    public record ServiceStatus(String name, int port, String state, String message) {
        public static ServiceStatus started(String name, int port) {
            return new ServiceStatus(name, port, "STARTED", "gestartet");
        }

        public static ServiceStatus alreadyRunning(String name, int port) {
            return new ServiceStatus(name, port, "ALREADY_RUNNING", "lief bereits");
        }

        public static ServiceStatus skipped(String name) {
            return new ServiceStatus(name, -1, "SKIPPED", "übersprungen");
        }

        public static ServiceStatus failed(String name, int port, String message) {
            return new ServiceStatus(name, port, "FAILED", message);
        }

        public boolean running() {
            return "STARTED".equals(state) || "ALREADY_RUNNING".equals(state);
        }
    }

    public record InitResult(
            ServiceStatus origin, ServiceStatus edge, ServiceStatus router, boolean success, String message) {}
}
