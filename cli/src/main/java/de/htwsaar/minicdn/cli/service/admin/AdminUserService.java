package de.htwsaar.minicdn.cli.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.dto.UserResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Admin-User-Service: spricht mit dem Router-Admin-API per TransportClient.
 * <p>
 * Endpunkte (Router):
 * - POST /api/cdn/admin/users        (CreateUserRequest)
 * - GET  /api/cdn/admin/users        (List<UserResult>)
 * - DELETE /api/cdn/admin/users/{id} (Delete user)
 * <p>
 * Alle Requests werden mit X-Admin-Token gesendet.
 */
public final class AdminUserService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;
    private final String adminToken;

    public AdminUserService(
            TransportClient transportClient, Duration requestTimeout, URI routerBaseUrl, String adminToken) {
        this.transportClient = Objects.requireNonNull(transportClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl);
        this.adminToken = Objects.requireNonNull(adminToken);
    }

    private URI base() {
        return UriUtils.ensureTrailingSlash(routerBaseUrl);
    }

    /**
     * Fügt einen Benutzer hinzu.
     */
    public HttpCallResult addUser(String name, int role) {
        try {
            URI url = base().resolve("api/cdn/admin/users");
            String json = MAPPER.writeValueAsString(Map.of("name", name, "role", role));

            TransportRequest request = TransportRequest.postJson(
                    url, requestTimeout, Map.of("X-Admin-Token", adminToken, "Content-Type", "application/json"), json);

            TransportResponse response = transportClient.send(request);
            return toHttpCallResult(response);
        } catch (Exception ex) {
            return HttpCallResult.ioError(ex.getMessage());
        }
    }

    /**
     * Listet alle Benutzer (roher HTTP-Result).
     */
    public HttpCallResult listUsersRaw() {
        try {
            URI url = base().resolve("api/cdn/admin/users");

            TransportRequest request = TransportRequest.get(url, requestTimeout, Map.of("X-Admin-Token", adminToken));

            TransportResponse response = transportClient.send(request);
            return toHttpCallResult(response);
        } catch (Exception ex) {
            return HttpCallResult.ioError(ex.getMessage());
        }
    }

    /**
     * Parsed JSON-Body zu UserResult-Liste.
     */
    public List<UserResult> parseUsers(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(body, new TypeReference<>() {});
    }

    /**
     * Löscht einen Benutzer nach ID.
     */
    public HttpCallResult deleteUser(long id) {
        try {
            URI url = base().resolve("api/cdn/admin/users/" + id);

            TransportRequest request =
                    TransportRequest.delete(url, requestTimeout, Map.of("X-Admin-Token", adminToken));

            TransportResponse response = transportClient.send(request);
            return toHttpCallResult(response);
        } catch (Exception ex) {
            return HttpCallResult.ioError(ex.getMessage());
        }
    }

    private static HttpCallResult toHttpCallResult(TransportResponse response) {
        if (response.error() != null) {
            return HttpCallResult.ioError(response.error());
        }
        return HttpCallResult.http(Objects.requireNonNull(response.statusCode(), "statusCode"), response.body());
    }
}
