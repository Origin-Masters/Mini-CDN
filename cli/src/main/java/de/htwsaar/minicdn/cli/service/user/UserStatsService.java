package de.htwsaar.minicdn.cli.service.user;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für User-spezifische Statistiken vom Router.
 */
public class UserStatsService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;
    private final String adminToken;

    public UserStatsService(
            TransportClient transportClient, Duration requestTimeout, URI routerBaseUrl, String adminToken) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.adminToken = Objects.requireNonNull(adminToken, "adminToken");
    }

    public HttpCallResult fileStatsForCurrentUser(long fileId) {
        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/stats/file/" + fileId);

        return sendGet(url);
    }

    public HttpCallResult listUserFilesStats(int limit) {
        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/stats/files?limit=" + limit);

        return sendGet(url);
    }

    public HttpCallResult overallStatsForCurrentUser(int windowSec) {
        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        URI url = base.resolve("api/cdn/stats?windowSec=" + windowSec);

        return sendGet(url);
    }

    private HttpCallResult sendGet(URI url) {
        TransportResponse response =
                transportClient.send(TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", adminToken)));

        if (response.error() != null) {
            return HttpCallResult.ioError(response.error());
        }

        return HttpCallResult.http(Objects.requireNonNull(response.statusCode(), "statusCode"), response.body());
    }
}
