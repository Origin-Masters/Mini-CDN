package de.htwsaar.minicdn.cli.adapter.out.http;

import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.LoginResult;
import de.htwsaar.minicdn.cli.domain.model.UserResult;
import de.htwsaar.minicdn.cli.domain.port.UserOperations;
import de.htwsaar.minicdn.common.serialization.JacksonCodec;
import de.htwsaar.minicdn.common.serialization.MiniCdnSerializationException;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-Adapter für benutzerbezogene CLI-Operationen.
 */
public final class HttpUserOperations implements UserOperations {

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    /**
     * Erzeugt den Adapter.
     *
     * @param transportClient HTTP-basierter Transportadapter
     * @param requestTimeout Standard-Timeout für Remote-Aufrufe
     */
    public HttpUserOperations(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /** {@inheritDoc} */
    @Override
    public LoginResult login(URI routerBaseUrl, String username) {
        String cleanUsername = HttpAdapterSupport.requireText(username, "username");

        try {
            TransportResponse response = transportClient.send(TransportRequest.postJson(
                    base(routerBaseUrl).resolve("api/cdn/auth/logins"),
                    requestTimeout,
                    HttpAdapterSupport.jsonHeaders(),
                    JacksonCodec.toJson(Map.of("name", cleanUsername))));
            return toLoginResult(response);
        } catch (MiniCdnSerializationException ex) {
            throw new IllegalArgumentException("failed to serialize login payload", ex);
        } catch (Exception ex) {
            return LoginResult.transportError(ex.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public CallResult fileStats(URI routerBaseUrl, long loggedInUserId, long fileId) {
        URI url = base(routerBaseUrl).resolve("api/cdn/stats/files/" + fileId);
        return HttpAdapterSupport.execute(
                transportClient,
                TransportRequest.get(url, requestTimeout, Map.of("X-User-Id", String.valueOf(loggedInUserId))));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult listFileStats(URI routerBaseUrl, long loggedInUserId, int limit) {
        URI url = base(routerBaseUrl).resolve("api/cdn/stats/files?limit=" + limit);
        return HttpAdapterSupport.execute(
                transportClient,
                TransportRequest.get(url, requestTimeout, Map.of("X-User-Id", String.valueOf(loggedInUserId))));
    }

    /** {@inheritDoc} */
    @Override
    public CallResult overallStats(URI routerBaseUrl, long loggedInUserId, int windowSec) {
        URI url = base(routerBaseUrl).resolve("api/cdn/stats?windowSec=" + windowSec);
        return HttpAdapterSupport.execute(
                transportClient,
                TransportRequest.get(url, requestTimeout, Map.of("X-User-Id", String.valueOf(loggedInUserId))));
    }

    private static LoginResult toLoginResult(TransportResponse response) {
        if (response == null) {
            return LoginResult.transportError("response must not be null");
        }
        if (response.error() != null) {
            return LoginResult.transportError(response.error());
        }

        int statusCode = Objects.requireNonNull(response.statusCode(), "statusCode");
        String rawBody = Objects.toString(response.body(), "");
        if (!response.is2xx()) {
            return statusCode >= 400 && statusCode < 500
                    ? LoginResult.rejected(statusCode, rawBody)
                    : LoginResult.serverError(statusCode, rawBody);
        }

        try {
            UserResult user = JacksonCodec.fromJson(rawBody, UserResult.class);
            return LoginResult.success(user, statusCode, rawBody);
        } catch (MiniCdnSerializationException ex) {
            return LoginResult.parsingError(statusCode, rawBody, ex.getMessage());
        }
    }

    private static URI base(URI routerBaseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(routerBaseUrl, "routerBaseUrl"));
    }
}
