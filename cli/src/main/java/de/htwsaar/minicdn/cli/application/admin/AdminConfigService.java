package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für Remote-Konfiguration von Origin- und Edge-Servern.
 *
 * <p>Die fachliche Logik validiert nur Eingaben. Technische Aufrufdetails
 * bleiben vollständig im Adapter. Rückgaben werden als
 * normierte fachliche Aufrufergebnisse modelliert.</p>
 */
public final class AdminConfigService {

    private final AdminOperations adminOperations;
    private final String adminToken;

    /**
     * Erzeugt den Service.
     *
     * @param adminOperations fachlicher Port für administrative Remote-Aufrufe
     * @param adminToken Admin-Token für geschützte Operationen
     */
    public AdminConfigService(AdminOperations adminOperations, String adminToken) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Liest die aktuelle Laufzeitkonfiguration des Origin-Servers.
     *
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult getOriginConfig(URI originBaseUrl) {
        return adminOperations.getOriginConfig(originBaseUrl, adminToken);
    }

    /**
     * Aktualisiert Teile der Laufzeitkonfiguration des Origin-Servers.
     *
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @param maxUploadBytes maximale Upload-Größe in Bytes, optional
     * @param logLevel Root-Log-Level, optional
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult patchOriginConfig(URI originBaseUrl, Long maxUploadBytes, String logLevel) {
        if (maxUploadBytes == null && !hasText(logLevel)) {
            return CallResult.clientError("at least one field must be provided");
        }
        return adminOperations.patchOriginConfig(originBaseUrl, adminToken, maxUploadBytes, logLevel);
    }

    /**
     * Liest den aktuellen Origin-Cluster-Zustand über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param checkHealth wenn true, werden Health-Checks je Origin ausgeführt
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult getOriginCluster(URI routerBaseUrl, boolean checkHealth) {
        return adminOperations.getOriginCluster(routerBaseUrl, adminToken, checkHealth);
    }

    /**
     * Registriert einen neuen Origin-Hot-Spare über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param spareBaseUrl Basis-URL des Hot-Spares
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult addOriginSpare(URI routerBaseUrl, URI spareBaseUrl) {
        return adminOperations.addOriginSpare(routerBaseUrl, adminToken, spareBaseUrl);
    }

    /**
     * Entfernt einen Origin-Hot-Spare über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param spareBaseUrl Basis-URL des Hot-Spares
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult removeOriginSpare(URI routerBaseUrl, URI spareBaseUrl) {
        return adminOperations.removeOriginSpare(routerBaseUrl, adminToken, spareBaseUrl);
    }

    /**
     * Befördert einen registrierten Hot-Spare zum aktiven Origin.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param spareBaseUrl Basis-URL des Hot-Spares
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult promoteOriginSpare(URI routerBaseUrl, URI spareBaseUrl) {
        return adminOperations.promoteOriginSpare(routerBaseUrl, adminToken, spareBaseUrl);
    }

    /**
     * Führt einen sofortigen Failover-Check des aktiven Origins aus.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult checkOriginFailover(URI routerBaseUrl) {
        return adminOperations.checkOriginFailover(routerBaseUrl, adminToken);
    }

    /**
     * Liest die aktuelle Laufzeitkonfiguration des Edge-Servers.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult getEdgeConfig(URI edgeBaseUrl) {
        return adminOperations.getEdgeConfig(edgeBaseUrl, adminToken);
    }

    /**
     * Aktualisiert Teile der Laufzeitkonfiguration des Edge-Servers.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @param region Region des Edge-Servers, optional
     * @param defaultTtlMs Standard-TTL in Millisekunden, optional
     * @param maxEntries maximale Cache-Einträge, optional
     * @param replacementStrategy Ersetzungsstrategie, optional
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult patchEdgeConfig(
            URI edgeBaseUrl, String region, Long defaultTtlMs, Integer maxEntries, String replacementStrategy) {
        if (!hasText(region) && defaultTtlMs == null && maxEntries == null && !hasText(replacementStrategy)) {
            return CallResult.clientError("at least one field must be provided");
        }
        return adminOperations.patchEdgeConfig(
                edgeBaseUrl, adminToken, region, defaultTtlMs, maxEntries, replacementStrategy, null);
    }

    /**
     * Liest alle TTL-Präfixregeln des Edge-Servers.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult getEdgeTtlPolicies(URI edgeBaseUrl) {
        return adminOperations.getEdgeTtlPolicies(edgeBaseUrl, adminToken);
    }

    /**
     * Setzt eine TTL-Regel für ein Pfad-Präfix auf dem Edge-Server.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @param prefix Pfad-Präfix
     * @param ttlMs TTL in Millisekunden
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult setEdgeTtlPolicy(URI edgeBaseUrl, String prefix, Long ttlMs) {
        if (ttlMs == null) {
            return CallResult.clientError("ttlMs must not be null");
        }
        return adminOperations.setEdgeTtlPolicy(edgeBaseUrl, adminToken, requireText(prefix, "prefix"), ttlMs);
    }

    /**
     * Entfernt eine TTL-Regel für ein Pfad-Präfix auf dem Edge-Server.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @param prefix Pfad-Präfix
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult removeEdgeTtlPolicy(URI edgeBaseUrl, String prefix) {
        return adminOperations.removeEdgeTtlPolicy(edgeBaseUrl, adminToken, requireText(prefix, "prefix"));
    }

    /**
     * Prüft, ob ein Text gesetzt ist.
     *
     * @param value zu prüfender Text
     * @return {@code true}, wenn der Text nicht leer ist
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Validiert einen Pflichttext und gibt die getrimmte Form zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
