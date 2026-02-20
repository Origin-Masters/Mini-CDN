package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.common.logging.TraceIdFilter;
import de.htwsaar.minicdn.router.dto.AutoStartEdgesRequest;
import de.htwsaar.minicdn.router.dto.AutoStartEdgesResponse;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.dto.StartEdgeRequest;
import de.htwsaar.minicdn.router.dto.StartEdgeResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Kapselt das Starten/Stoppen und Verwalten "managed" Edge-Prozesse.
 *
 * <p>Wichtig: Diese Klasse enthält absichtlich die komplette Ablauf-Logik,
 * damit der Controller nur noch HTTP-Übersetzung macht (SRP).</p>
 */
@Service
public class EdgeLifecycleService {

    /**
     * Interne Meta-Informationen zu einer managed Edge.
     *
     * @param instanceId Instance-ID (z.B. {@code edge-184647})
     * @param region Region der Edge
     * @param url Basis-URL der Edge
     * @param pid Prozess-ID
     */
    public record ManagedEdge(String instanceId, String region, String url, long pid) {}

    private final RoutingIndex routingIndex;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final Path edgeJarPath;

    private final int autoPortRangeStart;
    private final int autoPortRangeEnd;
    private final int maxAutoStartCount;

    private final Map<String, Process> managed = new ConcurrentHashMap<>();
    private final Map<String, ManagedEdge> managedMeta = new ConcurrentHashMap<>();
    private final Object startLock = new Object();

    public EdgeLifecycleService(
            RoutingIndex routingIndex,
            HttpClient httpClient,
            @Value("${cdn.edge-launcher.enabled:false}") boolean enabled,
            @Value("${cdn.edge-launcher.jar-path:edge/target/edge.jar}") String edgeJarPath,
            @Value("${cdn.edge-launcher.auto-port.range-start:10000}") int autoPortRangeStart,
            @Value("${cdn.edge-launcher.auto-port.range-end:20000}") int autoPortRangeEnd,
            @Value("${cdn.edge-launcher.auto-port.max-count:200}") int maxAutoStartCount) {
        this.routingIndex = Objects.requireNonNull(routingIndex, "routingIndex must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.enabled = enabled;
        this.edgeJarPath = Path.of(edgeJarPath);
        this.autoPortRangeStart = autoPortRangeStart;
        this.autoPortRangeEnd = autoPortRangeEnd;
        this.maxAutoStartCount = maxAutoStartCount;
    }

    /** Wirft eine Exception, wenn das Feature deaktiviert ist. */
    public void ensureEnabled() {
        if (!enabled) {
            throw new UnsupportedOperationException("Edge-Launcher ist deaktiviert (cdn.edge-launcher.enabled=false).");
        }
    }

    /** Startet genau eine Edge (serialisiert per Lock, inkl. Cleanup). */
    public StartEdgeResponse start(StartEdgeRequest req) throws Exception {
        validateStart(req);

        final String region = req.region().trim();
        final int port = req.port();

        synchronized (startLock) {
            cleanupDeadManagedProcesses();
            return startSingleEdgeLocked(region, port, req.originBaseUrl(), req.autoRegister(), req.waitUntilReady());
        }
    }

    /** Startet mehrere Edges atomar (Rollback bei Fehlern). */
    public AutoStartEdgesResponse startAuto(AutoStartEdgesRequest req) throws Exception {
        validateAutoStart(req);

        final String region = req.region().trim();
        final int count = req.count();

        synchronized (startLock) {
            cleanupDeadManagedProcesses();

            List<Integer> ports = allocateFreePortsLocked(count, autoPortRangeStart, autoPortRangeEnd);
            List<StartEdgeResponse> startedEdges = new ArrayList<>(ports.size());

            List<ManagedEdge> startedMeta = new ArrayList<>(ports.size());
            List<Process> startedProc = new ArrayList<>(ports.size());

            try {
                for (int port : ports) {
                    StartEdgeResponse started = startSingleEdgeLocked(
                            region, port, req.originBaseUrl(), req.autoRegister(), req.waitUntilReady());

                    startedEdges.add(started);

                    ManagedEdge meta = managedMeta.get(started.instanceId());
                    Process p = managed.get(started.instanceId());
                    if (meta != null && p != null) {
                        startedMeta.add(meta);
                        startedProc.add(p);
                    }
                }
            } catch (Exception ex) {
                if (req.autoRegister()) {
                    for (ManagedEdge meta : startedMeta) {
                        routingIndex.removeEdge(
                                meta.region(), new EdgeNode(UrlUtil.ensureTrailingSlash(meta.url())), true);
                    }
                }
                for (Process p : startedProc) {
                    stopBestEffort(p);
                }
                for (ManagedEdge meta : startedMeta) {
                    managed.remove(meta.instanceId());
                    managedMeta.remove(meta.instanceId());
                }
                throw ex;
            }

            return new AutoStartEdgesResponse(region, count, startedEdges.size(), startedEdges);
        }
    }

    /**
     * Stoppt eine managed Edge.
     *
     * @param instanceId Instance-ID aus {@code /api/cdn/admin/edges/managed}
     * @param deregister wenn {@code true}, wird die Edge aus dem RoutingIndex entfernt
     * @return {@code true}, wenn die Edge existierte und gestoppt wurde
     */
    public boolean stop(String instanceId, boolean deregister) {
        ManagedEdge meta = managedMeta.remove(instanceId);
        Process p = managed.remove(instanceId);

        if (meta == null || p == null) {
            return false;
        }

        if (deregister) {
            routingIndex.removeEdge(meta.region(), new EdgeNode(UrlUtil.ensureTrailingSlash(meta.url())), true);
        }

        stopBestEffort(p);
        return true;
    }

    /** Liefert die aktuell managed Edges (Meta-Daten) und räumt vorher stale Einträge auf. */
    public List<ManagedEdge> listManaged() {
        cleanupDeadManagedProcesses();
        return List.copyOf(managedMeta.values());
    }

    private void validateStart(StartEdgeRequest req) {
        if (req == null
                || req.region() == null
                || req.region().isBlank()
                || req.port() <= 0
                || req.port() > 65535
                || req.originBaseUrl() == null) {
            throw new IllegalArgumentException("Ungültige StartEdgeRequest-Daten.");
        }
        assertEdgeJarExists();
    }

    private void validateAutoStart(AutoStartEdgesRequest req) {
        if (req == null || req.region() == null || req.region().isBlank() || req.originBaseUrl() == null) {
            throw new IllegalArgumentException("Ungültige AutoStartEdgesRequest-Daten.");
        }
        if (req.count() <= 0) {
            throw new IllegalArgumentException("count muss > 0 sein.");
        }
        if (req.count() > maxAutoStartCount) {
            throw new IllegalArgumentException("count ist zu groß (max=" + maxAutoStartCount + ").");
        }
        validatePortRangeOrThrow();
        assertEdgeJarExists();
    }

    private void validatePortRangeOrThrow() {
        if (autoPortRangeStart <= 0
                || autoPortRangeStart > 65535
                || autoPortRangeEnd <= 0
                || autoPortRangeEnd > 65535
                || autoPortRangeStart > autoPortRangeEnd) {
            throw new IllegalStateException(
                    "Ungültiger Auto-Port-Range: " + autoPortRangeStart + "-" + autoPortRangeEnd);
        }
    }

    private void assertEdgeJarExists() {
        if (!Files.exists(edgeJarPath)) {
            throw new IllegalStateException(
                    "Edge-JAR nicht gefunden: " + edgeJarPath + " (baue zuerst das Edge-Modul).");
        }
    }

    private StartEdgeResponse startSingleEdgeLocked(
            String region, int port, URI originBaseUrl, boolean autoRegister, boolean waitUntilReady) throws Exception {

        ManagedEdge existingManaged = findManagedByPort(port);
        if (existingManaged != null) {
            throw new IllegalStateException(
                    "Port " + port + " ist bereits durch managed Edge belegt: " + existingManaged.instanceId());
        }

        if (!isTcpPortAvailable(port)) {
            throw new IllegalStateException("Port " + port + " ist bereits in Benutzung (anderer Prozess).");
        }

        final String url = "http://localhost:" + port;

        Process p = new ProcessBuilder(
                        "java",
                        "-jar",
                        edgeJarPath.toString(),
                        "--spring.profiles.active=edge",
                        "--server.port=" + port,
                        "--origin.base-url=" + originBaseUrl,
                        "--edge.region=" + region)
                .inheritIO()
                .start();

        String instanceId = "edge-" + p.pid();

        if (!p.isAlive()) {
            throw new IllegalStateException(
                    "Edge-Prozess ist direkt nach Start beendet (siehe Logs). instanceId=" + instanceId);
        }

        try {
            if (waitUntilReady) {
                waitUntilReady(p, URI.create(url), Duration.ofSeconds(8));
            }
        } catch (Exception startupEx) {
            stopBestEffort(p);
            throw startupEx;
        }

        managed.put(instanceId, p);
        managedMeta.put(instanceId, new ManagedEdge(instanceId, region, url, p.pid()));

        if (autoRegister) {
            routingIndex.addEdge(region, new EdgeNode(UrlUtil.ensureTrailingSlash(url)));
        }

        return new StartEdgeResponse(instanceId, url, p.pid(), region);
    }

    private List<Integer> allocateFreePortsLocked(int count, int rangeStart, int rangeEnd) {
        List<Integer> ports = new ArrayList<>(count);

        for (int port = rangeStart; port <= rangeEnd && ports.size() < count; port++) {
            if (findManagedByPort(port) != null) {
                continue;
            }
            if (!isTcpPortAvailable(port)) {
                continue;
            }
            ports.add(port);
        }

        if (ports.size() != count) {
            throw new IllegalStateException("Nicht genug freie Ports im Range " + rangeStart + "-" + rangeEnd
                    + " (benötigt=" + count + ", gefunden=" + ports.size() + ").");
        }

        return ports;
    }

    private void waitUntilReady(Process p, URI baseUrl, Duration timeout) throws Exception {
        URI ready = URI.create(UrlUtil.ensureTrailingSlash(baseUrl.toString()) + "api/edge/ready");
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive()) {
                throw new IllegalStateException(
                        "Edge-Prozess ist während des Startups gestorben! Exit-Code: " + p.exitValue());
            }

            try {
                HttpRequest req = withCurrentTraceId(
                                HttpRequest.newBuilder(ready).timeout(Duration.ofSeconds(1)))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return;
                }
            } catch (Exception ignored) {
            }

            Thread.sleep(150);
        }

        throw new IllegalStateException("Edge wurde nicht ready innerhalb " + timeout.toMillis() + "ms: " + baseUrl);
    }

    private static boolean isTcpPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

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

    private void cleanupDeadManagedProcesses() {
        for (String id : List.copyOf(managed.keySet())) {
            Process p = managed.get(id);
            if (p != null && !p.isAlive()) {
                managed.remove(id);
                managedMeta.remove(id);
            }
        }
    }

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

    private static HttpRequest.Builder withCurrentTraceId(HttpRequest.Builder builder) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            return builder;
        }
        return builder.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
    }
}
