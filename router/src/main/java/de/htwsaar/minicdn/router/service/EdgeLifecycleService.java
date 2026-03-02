package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.dto.AutoStartEdgesResponse;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.dto.StartEdgeResponse;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fachlicher Use Case zum Starten und Stoppen verwalteter Edge-Prozesse.
 *
 * <p>Die HTTP-Readiness-Prüfung erfolgt über den EdgeGateway-Port. Die konkrete
 * HTTP-Implementierung ist damit aus der Fachlogik entfernt.</p>
 */
@Service
public class EdgeLifecycleService {

    /**
     * Interne Meta-Informationen zu einer managed Edge.
     *
     * @param instanceId Instance-ID
     * @param region Region der Edge
     * @param url Basis-URL der Edge
     * @param pid Prozess-ID
     */
    public record ManagedEdge(String instanceId, String region, String url, long pid) {}

    private final RoutingIndex routingIndex;
    private final EdgeGateway edgeGateway;
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
            EdgeGateway edgeGateway,
            @Value("${cdn.edge-launcher.enabled:false}") boolean enabled,
            @Value("${cdn.edge-launcher.jar-path:edge/target/edge.jar}") String edgeJarPath,
            @Value("${cdn.edge-launcher.auto-port.range-start:10000}") int autoPortRangeStart,
            @Value("${cdn.edge-launcher.auto-port.range-end:20000}") int autoPortRangeEnd,
            @Value("${cdn.edge-launcher.auto-port.max-count:200}") int maxAutoStartCount) {
        this.routingIndex = Objects.requireNonNull(routingIndex, "routingIndex must not be null");
        this.edgeGateway = Objects.requireNonNull(edgeGateway, "edgeGateway must not be null");
        this.enabled = enabled;
        this.edgeJarPath = Path.of(edgeJarPath);
        this.autoPortRangeStart = autoPortRangeStart;
        this.autoPortRangeEnd = autoPortRangeEnd;
        this.maxAutoStartCount = maxAutoStartCount;
    }

    /**
     * Wirft eine Exception, wenn das Feature deaktiviert ist.
     */
    public void ensureEnabled() {
        if (!enabled) {
            throw new UnsupportedOperationException("Edge-Launcher ist deaktiviert (cdn.edge-launcher.enabled=false).");
        }
    }

    /**
     * Startet genau eine Edge.
     *
     * @param region Zielregion
     * @param port Port fuer die neue Instanz
     * @param originBaseUrl Basis-URL des Origin
     * @param autoRegister ob die Instanz automatisch registriert wird
     * @param waitUntilReady ob auf Readiness gewartet wird
     * @return Ergebnis des Starts
     */
    public StartEdgeResponse start(
            String region, int port, URI originBaseUrl, boolean autoRegister, boolean waitUntilReady) throws Exception {

        validateStart(region, port, originBaseUrl);

        synchronized (startLock) {
            cleanupDeadManagedProcesses();
            return startSingleEdgeLocked(region.trim(), port, originBaseUrl, autoRegister, waitUntilReady);
        }
    }

    /**
     * Startet mehrere Edges atomar mit Rollback bei Fehlern.
     *
     * @param region Zielregion
     * @param count Anzahl der Instanzen
     * @param originBaseUrl Basis-URL des Origin
     * @param autoRegister ob die Instanzen automatisch registriert werden
     * @param waitUntilReady ob auf Readiness gewartet wird
     * @return Ergebnis des Auto-Starts
     */
    public AutoStartEdgesResponse startAuto(
            String region, int count, URI originBaseUrl, boolean autoRegister, boolean waitUntilReady)
            throws Exception {

        validateAutoStart(region, count, originBaseUrl);

        final String cleanRegion = region.trim();

        synchronized (startLock) {
            cleanupDeadManagedProcesses();

            List<Integer> ports = allocateFreePortsLocked(count, autoPortRangeStart, autoPortRangeEnd);
            List<StartEdgeResponse> startedEdges = new ArrayList<>(ports.size());

            List<ManagedEdge> startedMeta = new ArrayList<>(ports.size());
            List<Process> startedProc = new ArrayList<>(ports.size());

            try {
                for (int port : ports) {
                    StartEdgeResponse started =
                            startSingleEdgeLocked(cleanRegion, port, originBaseUrl, autoRegister, waitUntilReady);

                    startedEdges.add(started);

                    ManagedEdge meta = managedMeta.get(started.instanceId());
                    Process process = managed.get(started.instanceId());
                    if (meta != null && process != null) {
                        startedMeta.add(meta);
                        startedProc.add(process);
                    }
                }
            } catch (Exception ex) {
                if (autoRegister) {
                    for (ManagedEdge meta : startedMeta) {
                        routingIndex.removeEdge(
                                meta.region(), new EdgeNode(UrlUtil.ensureTrailingSlash(meta.url())), true);
                    }
                }

                for (Process process : startedProc) {
                    stopBestEffort(process);
                }

                for (ManagedEdge meta : startedMeta) {
                    managed.remove(meta.instanceId());
                    managedMeta.remove(meta.instanceId());
                }

                throw ex;
            }

            return new AutoStartEdgesResponse(cleanRegion, count, startedEdges.size(), startedEdges);
        }
    }

    /**
     * Stoppt eine managed Edge.
     *
     * @param instanceId Instance-ID
     * @param deregister ob die Instanz deregistriert werden soll
     * @return true bei Erfolg
     */
    public boolean stop(String instanceId, boolean deregister) {
        ManagedEdge meta = managedMeta.remove(instanceId);
        Process process = managed.remove(instanceId);

        if (meta == null || process == null) {
            return false;
        }

        if (deregister) {
            routingIndex.removeEdge(meta.region(), new EdgeNode(UrlUtil.ensureTrailingSlash(meta.url())), true);
        }

        stopBestEffort(process);
        return true;
    }

    /**
     * Stoppt alle managed Edges einer Region.
     *
     * @param region Zielregion
     * @param deregister ob die Instanzen deregistriert werden sollen
     * @return Anzahl erfolgreich gestoppter Instanzen
     */
    public int stopRegion(String region, boolean deregister) {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("Region darf nicht leer sein.");
        }

        synchronized (startLock) {
            cleanupDeadManagedProcesses();

            String cleanRegion = region.trim();
            List<String> instanceIds = managedMeta.values().stream()
                    .filter(meta -> cleanRegion.equals(meta.region()))
                    .map(ManagedEdge::instanceId)
                    .toList();

            int stopped = 0;
            for (String instanceId : instanceIds) {
                if (stop(instanceId, deregister)) {
                    stopped++;
                }
            }
            return stopped;
        }
    }

    /**
     * Liefert die aktuell verwalteten Edge-Instanzen.
     *
     * @return Liste der Instanzen
     */
    public List<ManagedEdge> listManaged() {
        cleanupDeadManagedProcesses();
        return List.copyOf(managedMeta.values());
    }

    /**
     * Validiert Eingaben fuer den Start einer einzelnen Edge.
     */
    private void validateStart(String region, int port, URI originBaseUrl) {
        if (region == null || region.isBlank() || port <= 0 || port > 65535 || originBaseUrl == null) {
            throw new IllegalArgumentException("Ungültige StartEdgeRequest-Daten.");
        }
        assertEdgeJarExists();
    }

    /**
     * Validiert Eingaben fuer den Auto-Start.
     */
    private void validateAutoStart(String region, int count, URI originBaseUrl) {
        if (region == null || region.isBlank() || originBaseUrl == null) {
            throw new IllegalArgumentException("Ungültige AutoStartEdgesRequest-Daten.");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count muss > 0 sein.");
        }
        if (count > maxAutoStartCount) {
            throw new IllegalArgumentException("count ist zu groß (max=" + maxAutoStartCount + ").");
        }
        validatePortRangeOrThrow();
        assertEdgeJarExists();
    }

    /**
     * Validiert den Auto-Port-Range.
     */
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

    /**
     * Stellt sicher, dass das Edge-JAR existiert.
     */
    private void assertEdgeJarExists() {
        if (!Files.exists(edgeJarPath)) {
            throw new IllegalStateException(
                    "Edge-JAR nicht gefunden: " + edgeJarPath + " (baue zuerst das Edge-Modul).");
        }
    }

    /**
     * Startet eine Edge unter Lock und registriert sie optional.
     */
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

        String url = "http://localhost:" + port;

        Process process = new ProcessBuilder(
                        "java",
                        "-jar",
                        edgeJarPath.toString(),
                        "--spring.profiles.active=edge",
                        "--server.port=" + port,
                        "--origin.base-url=" + originBaseUrl,
                        "--edge.region=" + region)
                .inheritIO()
                .start();

        String instanceId = "edge-" + process.pid();

        if (!process.isAlive()) {
            throw new IllegalStateException(
                    "Edge-Prozess ist direkt nach Start beendet (siehe Logs). instanceId=" + instanceId);
        }

        try {
            if (waitUntilReady) {
                waitUntilReady(process, URI.create(url), Duration.ofSeconds(8));
            }
        } catch (Exception startupEx) {
            stopBestEffort(process);
            throw startupEx;
        }

        managed.put(instanceId, process);
        managedMeta.put(instanceId, new ManagedEdge(instanceId, region, url, process.pid()));

        if (autoRegister) {
            routingIndex.addEdge(region, new EdgeNode(UrlUtil.ensureTrailingSlash(url)));
        }

        return new StartEdgeResponse(instanceId, url, process.pid(), region);
    }

    /**
     * Allokiert freie Ports innerhalb des Ranges.
     */
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

    /**
     * Wartet bis die Edge ready ist oder der Timeout ablaeuft.
     */
    private void waitUntilReady(Process process, URI baseUrl, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "Edge-Prozess ist während des Startups gestorben! Exit-Code: " + process.exitValue());
            }

            if (edgeGateway.isReady(baseUrl, Duration.ofSeconds(1))) {
                return;
            }

            Thread.sleep(150);
        }

        throw new IllegalStateException("Edge wurde nicht ready innerhalb " + timeout.toMillis() + "ms: " + baseUrl);
    }

    /**
     * Prueft, ob ein TCP-Port frei ist.
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
     * Sucht eine managed Edge anhand des Ports.
     */
    private ManagedEdge findManagedByPort(int port) {
        for (ManagedEdge managedEdge : managedMeta.values()) {
            try {
                if (URI.create(managedEdge.url()).getPort() == port) {
                    return managedEdge;
                }
            } catch (Exception ignored) {
                // ignorieren
            }
        }
        return null;
    }

    /**
     * Entfernt nicht mehr laufende Prozesse aus den Maps.
     */
    private void cleanupDeadManagedProcesses() {
        for (String id : List.copyOf(managed.keySet())) {
            Process process = managed.get(id);
            if (process != null && !process.isAlive()) {
                managed.remove(id);
                managedMeta.remove(id);
            }
        }
    }

    /**
     * Stoppt einen Prozess best-effort mit Fallback auf destroyForcibly.
     */
    private static void stopBestEffort(Process process) {
        try {
            if (process.isAlive()) {
                process.destroy();
                process.waitFor(800, TimeUnit.MILLISECONDS);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
