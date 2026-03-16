package de.htwsaar.minicdn.cli.domain.port;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.StatsResponse;
import java.net.URI;
import java.nio.file.Path;

/**
 * Fachlicher Port für administrative Remote-Operationen der CLI.
 *
 * <p>Die fachliche Logik kennt hier ausschließlich Use-Cases. Konkrete
 * Implementierungsdetails der technischen Anbindung bleiben vollständig in den
 * Adapter-Implementierungen.</p>
 */
public interface AdminOperations {

    /** Führt einen einfachen Health-Check gegen einen relativen Pfad aus. */
    CallResult ping(URI baseUrl, String relativePath);

    /** Invalidiert genau eine Datei im Regions-Cache. */
    CallResult invalidateFile(URI routerBaseUrl, String adminToken, String region, String path);

    /** Invalidiert alle Dateien eines Präfixes im Regions-Cache. */
    CallResult invalidatePrefix(URI routerBaseUrl, String adminToken, String region, String prefix);

    /** Leert den kompletten Cache einer Region. */
    CallResult clearRegion(URI routerBaseUrl, String adminToken, String region);

    /** Liest die Origin-Konfiguration. */
    CallResult getOriginConfig(URI originBaseUrl, String adminToken);

    /** Aktualisiert Teile der Origin-Konfiguration. */
    CallResult patchOriginConfig(URI originBaseUrl, String adminToken, Long maxUploadBytes, String logLevel);

    /** Liest den aktuellen Origin-Cluster-Zustand. */
    CallResult getOriginCluster(URI routerBaseUrl, String adminToken, boolean checkHealth);

    /** Registriert einen Origin-Hot-Spare. */
    CallResult addOriginSpare(URI routerBaseUrl, String adminToken, URI spareBaseUrl);

    /** Entfernt einen Origin-Hot-Spare. */
    CallResult removeOriginSpare(URI routerBaseUrl, String adminToken, URI spareBaseUrl);

    /** Befördert einen Hot-Spare zum aktiven Origin. */
    CallResult promoteOriginSpare(URI routerBaseUrl, String adminToken, URI spareBaseUrl);

    /** Führt einen Failover-Check für den Origin aus. */
    CallResult checkOriginFailover(URI routerBaseUrl, String adminToken);

    /** Liest die Edge-Konfiguration. */
    CallResult getEdgeConfig(URI edgeBaseUrl, String adminToken);

    /** Aktualisiert Teile der Edge-Konfiguration. */
    CallResult patchEdgeConfig(
            URI edgeBaseUrl,
            String adminToken,
            String region,
            Long defaultTtlMs,
            Integer maxEntries,
            String replacementStrategy,
            URI originBaseUrl);

    /** Liest die TTL-Regeln eines Edge-Servers. */
    CallResult getEdgeTtlPolicies(URI edgeBaseUrl, String adminToken);

    /** Setzt eine TTL-Regel für ein Präfix. */
    CallResult setEdgeTtlPolicy(URI edgeBaseUrl, String adminToken, String prefix, Long ttlMs);

    /** Entfernt eine TTL-Regel für ein Präfix. */
    CallResult removeEdgeTtlPolicy(URI edgeBaseUrl, String adminToken, String prefix);

    /** Startet eine verwaltete Edge-Instanz. */
    CallResult startEdge(
            URI routerBaseUrl,
            String adminToken,
            String region,
            int port,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady);

    /** Stoppt eine verwaltete Edge-Instanz. */
    CallResult stopEdge(URI routerBaseUrl, String adminToken, String instanceId, boolean deregister);

    /** Stoppt alle verwalteten Edges einer Region. */
    CallResult stopRegion(URI routerBaseUrl, String adminToken, String region, boolean deregister);

    /** Listet alle verwalteten Edge-Instanzen auf. */
    CallResult listManagedEdges(URI routerBaseUrl, String adminToken);

    /** Startet mehrere verwaltete Edges automatisch. */
    CallResult startEdgesAuto(
            URI routerBaseUrl,
            String adminToken,
            String region,
            int count,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady);

    /** Lädt eine Datei über den Router hoch. */
    CallResult uploadFile(
            URI routerBaseUrl,
            String adminToken,
            long loggedInUserId,
            String targetPath,
            Path localFile,
            String region);

    /** Löscht eine Datei über den Router. */
    CallResult deleteFile(URI routerBaseUrl, String adminToken, long loggedInUserId, String targetPath, String region);

    /** Listet Dateien über den Router auf. */
    CallResult listFiles(URI routerBaseUrl, String adminToken, long loggedInUserId);

    /** Liest Metadaten einer Datei über den Router. */
    CallResult showFile(URI routerBaseUrl, String adminToken, long loggedInUserId, String targetPath);

    /** Registriert einen Routing-Knoten. */
    CallResult addRoutingNode(URI routerBaseUrl, String adminToken, String region, URI edgeBaseUrl);

    /** Entfernt einen Routing-Knoten. */
    CallResult removeRoutingNode(URI routerBaseUrl, String adminToken, String region, URI edgeBaseUrl);

    /** Liest den Routing-Zustand. */
    CallResult listRoutingNodes(URI routerBaseUrl, String adminToken, boolean checkHealth);

    /** Führt ein Bulk-Update des Routings aus. */
    CallResult bulkUpdateRouting(URI routerBaseUrl, String adminToken, String jsonBody);

    /** Legt einen Benutzer an. */
    CallResult addUser(URI routerBaseUrl, String adminToken, long loggedInUserId, String name, int role);

    /** Listet Benutzer auf. */
    CallResult listUsers(URI routerBaseUrl, String adminToken, long loggedInUserId);

    /** Löscht einen Benutzer. */
    CallResult deleteUser(URI routerBaseUrl, String adminToken, long loggedInUserId, long id);

    /** Lädt strukturierte Laufzeitstatistiken. */
    StatsResponse fetchStats(URI routerBaseUrl, int windowSec, boolean aggregateEdge, String adminToken);
}
