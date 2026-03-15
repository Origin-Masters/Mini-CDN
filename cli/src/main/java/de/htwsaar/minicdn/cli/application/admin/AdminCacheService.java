package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.application.support.TransportCallAdapter;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.TransportRequest;
import de.htwsaar.minicdn.cli.domain.port.TransportClient;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für regionenweite Cache-Invalidierung über den Router.
 */
public final class AdminCacheService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final String adminToken;

    public AdminCacheService(TransportClient transportClient, Duration requestTimeout, String adminToken) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Invalidiert genau eine gecachte Datei in einer Region.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param path relativer Dateipfad
     * @return normiertes Request-Ergebnis
     */
    public CallResult invalidateFile(URI routerBaseUrl, String region, String path) {
        String cleanRegion = UriUtils.urlEncode(requireText(region, "region"));
        String cleanPath = PathUtils.normalizeRelativePath(path);
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/cache/region/" + cleanRegion + "/files/" + cleanPath);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Invalidiert alle Cache-Einträge eines Präfix in einer Region.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param prefix relatives Dateipraefix
     * @return normiertes Request-Ergebnis
     */
    public CallResult invalidatePrefix(URI routerBaseUrl, String region, String prefix) {
        String cleanRegion = UriUtils.urlEncode(requireText(region, "region"));
        String cleanPrefix = UriUtils.urlEncode(PathUtils.normalizeRelativePath(prefix));
        URI url = base(routerBaseUrl)
                .resolve("api/cdn/admin/cache/region/" + cleanRegion + "/prefix?value=" + cleanPrefix);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Leert den Cache einer Region vollständig.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @return normiertes Request-Ergebnis
     */
    public CallResult clearRegion(URI routerBaseUrl, String region) {
        String cleanRegion = UriUtils.urlEncode(requireText(region, "region"));
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/cache/region/" + cleanRegion + "/all");
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    private CallResult send(TransportRequest request) {
        return TransportCallAdapter.execute(transportClient, request);
    }

    private Map<String, String> adminHeaders() {
        return Map.of("X-Admin-Token", adminToken);
    }

    private static URI base(URI routerBaseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(routerBaseUrl, "routerBaseUrl"));
    }

    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
