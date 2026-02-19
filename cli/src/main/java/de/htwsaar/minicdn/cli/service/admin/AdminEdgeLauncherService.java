package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.util.HttpUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP-Client-Service für die Admin-API des Routers zum Starten/Stoppen und Auflisten
 * von "managed" Edge-Instanzen (Edge-Lifecycle).
 *
 * <p>Diese Klasse kapselt URL-Aufbau, JSON-Payloads und HTTP-Error-Handling (IO vs. HTTP-Status),
 * damit Picocli-Commands schlank bleiben (SRP).
 */
public final class AdminEdgeLauncherService {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * Erstellt den Service.
     *
     * @param httpClient gemeinsamer HTTP-Client
     * @param requestTimeout Timeout pro Request
     */
    public AdminEdgeLauncherService(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * POST /api/cdn/admin/edges/start
     *
     * @param routerBaseUrl Router Base-URL, z.B. http://localhost:8080
     * @param region Zielregion (nicht blank)
     * @param port Edge-Port (1..65535)
     * @param originBaseUrl Origin Base-URL (http/https)
     * @param autoRegister ob nach dem Start im RoutingIndex registriert wird
     * @param waitUntilReady ob der Router aktiv auf /api/edge/ready warten soll
     * @return HTTP-Ergebnis (Status/Body) oder IO-Error
     */
    public HttpCallResult startEdge(
            URI routerBaseUrl,
            String region,
            int port,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/start");

        String json = "{"
                + "\"region\":\"" + JsonUtils.escapeJson(region.trim()) + "\","
                + "\"port\":" + port + ","
                + "\"originBaseUrl\":\"" + JsonUtils.escapeJson(originBaseUrl.toString()) + "\","
                + "\"autoRegister\":" + autoRegister + ","
                + "\"waitUntilReady\":" + waitUntilReady
                + "}";

        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return HttpUtils.sendForStringBody(httpClient, req);
    }

    /**
     * DELETE /api/cdn/admin/edges/{instanceId}?deregister=true|false
     *
     * @param routerBaseUrl Router Base-URL
     * @param instanceId Instance-ID, z.B. edge-12345 (nur sichere Zeichen erlaubt)
     * @param deregister ob die Edge aus dem RoutingIndex deregistriert werden soll
     * @return HTTP-Ergebnis (Status/Body) oder IO-Error
     */
    public HttpCallResult stopEdge(URI routerBaseUrl, String instanceId, boolean deregister) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(instanceId, "instanceId");

        String trimmed = instanceId.trim();
        if (!isSafeInstanceId(trimmed)) {
            return HttpCallResult.clientError("Invalid instanceId (expected pattern: [A-Za-z0-9_-]+).");
        }

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/" + trimmed + "?deregister=" + deregister);

        HttpRequest req =
                HttpRequest.newBuilder(url).timeout(requestTimeout).DELETE().build();

        return HttpUtils.sendForStringBody(httpClient, req);
    }

    /**
     * GET /api/cdn/admin/edges/managed
     *
     * @param routerBaseUrl Router Base-URL
     * @return HTTP-Ergebnis (Status/Body) oder IO-Error
     */
    public HttpCallResult listManaged(URI routerBaseUrl) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/managed");

        HttpRequest req =
                HttpRequest.newBuilder(url).timeout(requestTimeout).GET().build();

        return HttpUtils.sendForStringBody(httpClient, req);
    }

    /**
     * Startet mehrere Edge-Prozesse mit automatischer Portvergabe über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers (z.B. http://localhost:8082)
     * @param region Zielregion (z.B. EU)
     * @param count Anzahl zu startender Edges (> 0)
     * @param originBaseUrl Origin-Basis-URL (z.B. http://localhost:8080)
     * @param autoRegister Wenn true, registriert der Router die gestarteten Edges automatisch
     * @param waitUntilReady Wenn true, wartet der Router pro Edge auf Readiness
     * @return HTTP-Ergebnis inkl. Body oder Fehlertext
     */
    public HttpCallResult startEdgesAuto(
            URI routerBaseUrl,
            String region,
            int count,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/admin/edges/start/auto");

        String json = String.format(
                "{\"region\":\"%s\",\"count\":%d,\"originBaseUrl\":\"%s\",\"autoRegister\":%s,\"waitUntilReady\":%s}",
                JsonUtils.escapeJson(region),
                count,
                JsonUtils.escapeJson(originBaseUrl.toString()),
                autoRegister,
                waitUntilReady);

        HttpRequest req = HttpRequest.newBuilder(url)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return HttpUtils.sendForStringBody(httpClient, req);
    }

    private static boolean isSafeInstanceId(String s) {
        return !s.isBlank() && s.matches("[A-Za-z0-9_-]+");
    }
}
