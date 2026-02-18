package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API zum Starten (Dev/Demo) und Verwalten von Edge-Instanzen über den Router.
 *
 * <p><strong>Wichtige Eigenschaften</strong>:</p>
 * <ul>
 *   <li>Vor dem Start wird geprüft, ob der gewünschte TCP-Port bereits belegt ist (OS-Level Bind-Check).</li>
 *   <li>Zusätzlich wird geprüft, ob bereits eine managed Edge auf diesem Port läuft (Registry-Check).</li>
 *   <li>Start-Vorgänge werden serialisiert, um Race-Conditions bei parallelen Requests zu vermeiden.</li>
 *   <li>Ein Start gilt erst als erfolgreich, wenn die Edge innerhalb eines Timeouts "ready" wird (optional).</li>
 *   <li>Stop ist best-effort und liefert keine 500 für normale "schon beendet"-Fälle.</li>
 * </ul>
 */
@RestController
@RequestMapping("api/cdn/admin/edges")
public class EdgeLifecycleController {

    /**
     * Request zum Starten einer Edge-Instanz.
     *
     * @param region Ziel-Region, in die die Edge optional registriert wird (z.B. {@code EU})
     * @param port TCP-Port für den Edge-HTTP-Server (1..65535)
     * @param originBaseUrl Basis-URL des Origin-Servers, die an die Edge übergeben wird
     * @param autoRegister Wenn {@code true}, wird die Edge nach erfolgreichem Start in den {@link RoutingIndex} eingetragen
     * @param waitUntilReady Wenn {@code true}, wartet der Router aktiv auf {@code /api/edge/ready}
     */
    public record StartEdgeRequest(
            String region, int port, URI originBaseUrl, boolean autoRegister, boolean waitUntilReady) {}

    /**
     * Response nach erfolgreichem Start einer Edge-Instanz.
     *
     * @param instanceId vom Router vergebene Instance-ID (Format: {@code edge-<pid>})
     * @param url Basis-URL der Edge (z.B. {@code http://localhost:8081})
     * @param pid OS-Prozess-ID der gestarteten Edge
     * @param region Region, in die die Edge gestartet wurde
     */
    public record StartEdgeResponse(String instanceId, String url, long pid, String region) {}

    /**
     * Interne Meta-Informationen zu einer managed Edge.
     *
     * @param instanceId Instance-ID (z.B. {@code edge-184647})
     * @param region Region der Edge
     * @param url Basis-URL der Edge
     * @param pid Prozess-ID
     */
    private record ManagedEdge(String instanceId, String region, String url, long pid) {}

    private final RoutingIndex routingIndex;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final Path edgeJarPath;

    /**
     * Registry der laufenden managed Prozesse (Instance-ID -> Process).
     *
     * <p>Hinweis: Prozesse werden zusätzlich in {@link #managedMeta} gespiegelt, um Port/Region/URL abzufragen.</p>
     */
    private final Map<String, Process> managed = new ConcurrentHashMap<>();

    /**
     * Registry der Meta-Daten für managed Prozesse (Instance-ID -> Meta).
     */
    private final Map<String, ManagedEdge> managedMeta = new ConcurrentHashMap<>();

    /**
     * Sperre, um Start-Vorgänge zu serialisieren (Port-Checks + Start müssen atomar sein).
     */
    private final Object startLock = new Object();

    public EdgeLifecycleController(
            RoutingIndex routingIndex,
            HttpClient httpClient,
            @Value("${cdn.edge-launcher.enabled:false}") boolean enabled,
            @Value("${cdn.edge-launcher.jar-path:edge/target/edge.jar}") String edgeJarPath) {
        this.routingIndex = Objects.requireNonNull(routingIndex, "routingIndex must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.enabled = enabled;
        this.edgeJarPath = Path.of(edgeJarPath);
    }

    /**
     * Startet eine Edge als separaten Java-Prozess.
     *
     * <p>Es wird verhindert, dass derselbe Port mehrfach belegt wird (Registry-Check + OS-Level Port-Check).</p>
     *
     * @param req Start-Request
     * @return {@code 201 Created} bei Erfolg, sonst {@code 409 Conflict} mit Fehlermeldung
     */
    @PostMapping("start")
    public ResponseEntity<?> start(@RequestBody StartEdgeRequest req) {
        ensureEnabled();

        try {
            validateStart(req);

            final String region = req.region().trim();
            final int port = req.port();
            final String url = "http://localhost:" + port;

            synchronized (startLock) {
                cleanupDeadManagedProcesses();

                ManagedEdge existingManaged = findManagedByPort(port);
                if (existingManaged != null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("Port " + port + " ist bereits durch managed Edge belegt: "
                                    + existingManaged.instanceId());
                }

                if (!isTcpPortAvailable(port)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("Port " + port + " ist bereits in Benutzung (anderer Prozess).");
                }

                Process p = new ProcessBuilder(
                                "java",
                                "-jar",
                                edgeJarPath.toString(),
                                "--spring.profiles.active=edge",
                                "--server.port=" + port,
                                "--origin.base-url=" + req.originBaseUrl(),
                                "--edge.region=" + region)
                        .inheritIO()
                        .start();

                String instanceId = "edge-" + p.pid();

                if (!p.isAlive()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body("Edge-Prozess ist direkt nach Start beendet (siehe Logs). instanceId=" + instanceId);
                }

                try {
                    if (req.waitUntilReady()) {
                        waitUntilReady(p, URI.create(url), Duration.ofSeconds(8));
                    }
                } catch (Exception startupEx) {
                    stopBestEffort(p);
                    throw startupEx;
                }

                managed.put(instanceId, p);
                managedMeta.put(instanceId, new ManagedEdge(instanceId, region, url, p.pid()));

                if (req.autoRegister()) {
                    routingIndex.addEdge(region, new EdgeNode(UrlUtil.ensureTrailingSlash(url)));
                }

                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new StartEdgeResponse(instanceId, url, p.pid(), region));
            }
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Edge-Start fehlgeschlagen: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Stoppt eine managed Edge.
     *
     * @param instanceId ID aus {@code /api/cdn/admin/edges/managed}
     * @param deregister wenn {@code true}, wird die Edge aus dem {@link RoutingIndex} entfernt
     * @return {@code 200 OK} bei Erfolg, {@code 404 Not Found} wenn nicht (mehr) managed
     */
    @DeleteMapping("{instanceId}")
    public ResponseEntity<?> delete(
            @PathVariable("instanceId") String instanceId,
            @RequestParam(name = "deregister", defaultValue = "true") boolean deregister) {

        ensureEnabled();

        ManagedEdge meta = managedMeta.remove(instanceId);
        Process p = managed.remove(instanceId);

        if (meta == null || p == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Unbekannte instanceId (nicht managed): " + instanceId);
        }

        if (deregister) {
            routingIndex.removeEdge(meta.region(), new EdgeNode(UrlUtil.ensureTrailingSlash(meta.url())), true);
        }

        stopBestEffort(p);
        return ResponseEntity.ok().build();
    }

    /**
     * Liefert die aktuell managed Edges (Meta-Daten).
     *
     * @return Liste aller managed Edge-Metaeinträge
     */
    @GetMapping("managed")
    public ResponseEntity<?> listManaged() {
        ensureEnabled();
        cleanupDeadManagedProcesses();
        return ResponseEntity.ok(managedMeta.values());
    }

    /**
     * Prüft, ob das Edge-Launcher Feature aktiviert ist.
     *
     * @throws UnsupportedOperationException wenn {@code cdn.edge-launcher.enabled=false}
     */
    private void ensureEnabled() {
        if (!enabled) {
            throw new UnsupportedOperationException("Edge-Launcher ist deaktiviert (cdn.edge-launcher.enabled=false).");
        }
    }

    /**
     * Validiert die Start-Parameter inkl. Portbereich und JAR-Existenz.
     *
     * @param req Start-Request
     * @throws IllegalArgumentException bei ungültigen Parametern
     * @throws IllegalStateException wenn die Edge-JAR nicht existiert
     */
    private void validateStart(StartEdgeRequest req) {
        if (req == null
                || req.region() == null
                || req.region().isBlank()
                || req.port() <= 0
                || req.port() > 65535
                || req.originBaseUrl() == null) {
            throw new IllegalArgumentException("Ungültige StartEdgeRequest-Daten.");
        }
        if (!Files.exists(edgeJarPath)) {
            throw new IllegalStateException(
                    "Edge-JAR nicht gefunden: " + edgeJarPath + " (baue zuerst das Edge-Modul).");
        }
    }

    /**
     * Wartet aktiv auf den Readiness-Endpunkt einer Edge.
     *
     * <p>Bricht sofort ab, wenn der gestartete Prozess stirbt, um schnelle Diagnose zu ermöglichen.</p>
     *
     * @param p gestarteter Prozess
     * @param baseUrl Basis-URL (z.B. {@code http://localhost:8081})
     * @param timeout Gesamt-Timeout für Readiness
     * @throws Exception wenn Readiness nicht erreicht wird oder Prozess während des Startups stirbt
     */
    private void waitUntilReady(Process p, URI baseUrl, Duration timeout) throws Exception {
        URI ready = URI.create(UrlUtil.ensureTrailingSlash(baseUrl.toString()) + "api/edge/ready");
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive()) {
                int exitCode = p.exitValue();
                throw new IllegalStateException(
                        "Edge-Prozess ist während des Startups gestorben! Exit-Code: " + exitCode);
            }

            try {
                HttpRequest req = HttpRequest.newBuilder(ready)
                        .timeout(Duration.ofSeconds(1))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return;
                }
            } catch (Exception ignored) {
                // Connection refused etc. -> wir warten weiter
            }

            Thread.sleep(150);
        }

        throw new IllegalStateException("Edge wurde nicht ready innerhalb " + timeout.toMillis() + "ms: " + baseUrl);
    }

    /**
     * Prüft OS-nah, ob ein TCP-Port aktuell bindbar ist.
     *
     * <p>Implementierung: kurzzeitiges Binden eines {@link ServerSocket} an {@code 0.0.0.0:port}.</p>
     *
     * @param port zu prüfender TCP-Port
     * @return {@code true}, wenn der Port frei ist, sonst {@code false}
     */
    private static boolean isTcpPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Sucht eine managed Edge anhand ihres TCP-Ports.
     *
     * @param port TCP-Port
     * @return managed Edge oder {@code null}, wenn keine managed Edge diesen Port nutzt
     */
    private ManagedEdge findManagedByPort(int port) {
        for (ManagedEdge m : managedMeta.values()) {
            try {
                if (URI.create(m.url()).getPort() == port) {
                    return m;
                }
            } catch (Exception ignored) {
                // malformed URL -> ignorieren
            }
        }
        return null;
    }

    /**
     * Räumt stale managed Einträge auf (Prozess ist nicht mehr alive).
     *
     * <p>Wichtig, damit Port-Prüfungen und /managed verlässlich bleiben.</p>
     */
    private void cleanupDeadManagedProcesses() {
        for (String id : managed.keySet()) {
            Process p = managed.get(id);
            if (p != null && !p.isAlive()) {
                managed.remove(id);
                managedMeta.remove(id);
            }
        }
    }

    /**
     * Stoppt einen Prozess best-effort, ohne 500 für normale Fälle zu verursachen.
     *
     * @param p Prozess
     */
    private static void stopBestEffort(Process p) {
        try {
            if (p.isAlive()) {
                p.destroy();
                p.waitFor(800, TimeUnit.MILLISECONDS);
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }
}
