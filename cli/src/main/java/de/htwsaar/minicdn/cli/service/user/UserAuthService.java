package de.htwsaar.minicdn.cli.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.dto.UserResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Service für den User-Login über die Router-Auth-API.
 */
public final class UserAuthService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;

    public UserAuthService(TransportClient transportClient, Duration requestTimeout, URI routerBaseUrl) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
    }

    public LoginResult login(String username) {
        try {
            URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
            URI url = base.resolve("api/cdn/auth/login");
            String payload = MAPPER.writeValueAsString(Map.of("name", username));

            TransportResponse response = transportClient.send(TransportRequest.postJson(
                    url, requestTimeout, Map.of("Content-Type", "application/json"), payload));

            if (response.error() != null) {
                return LoginResult.ioError(response.error());
            }

            int statusCode = Objects.requireNonNull(response.statusCode(), "statusCode");
            if (statusCode < 200 || statusCode >= 300) {
                return LoginResult.httpError(statusCode);
            }

            UserResult user = MAPPER.readValue(response.body(), UserResult.class);
            return LoginResult.success(user);
        } catch (Exception ex) {
            return LoginResult.ioError(ex.getMessage());
        }
    }

    public record LoginResult(UserResult user, Integer statusCode, String error) {

        public static LoginResult success(UserResult user) {
            return new LoginResult(user, 200, null);
        }

        public static LoginResult httpError(int statusCode) {
            return new LoginResult(null, statusCode, null);
        }

        public static LoginResult ioError(String error) {
            return new LoginResult(null, null, error);
        }
    }
}
