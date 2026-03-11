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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdminUserService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;
    private final String adminToken;
    private final long loggedInUserId;

    public AdminUserService(
            TransportClient transportClient,
            Duration requestTimeout,
            URI routerBaseUrl,
            String adminToken,
            long loggedInUserId) {
        this.transportClient = Objects.requireNonNull(transportClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl);
        this.adminToken = Objects.requireNonNull(adminToken);
        this.loggedInUserId = loggedInUserId;
    }

    private URI base() {
        return UriUtils.ensureTrailingSlash(routerBaseUrl);
    }

    public HttpCallResult addUser(String name, int role) {
        try {
            URI url = base().resolve("api/cdn/admin/users");
            String json = MAPPER.writeValueAsString(Map.of("name", name, "role", role));

            TransportRequest request = TransportRequest.postJson(url, requestTimeout, adminJsonHeaders(), json);

            TransportResponse response = transportClient.send(request);
            return toHttpCallResult(response);
        } catch (Exception ex) {
            return HttpCallResult.ioError(ex.getMessage());
        }
    }

    public HttpCallResult listUsersRaw() {
        try {
            URI url = base().resolve("api/cdn/admin/users");

            TransportRequest request = TransportRequest.get(url, requestTimeout, adminHeaders());

            TransportResponse response = transportClient.send(request);
            return toHttpCallResult(response);
        } catch (Exception ex) {
            return HttpCallResult.ioError(ex.getMessage());
        }
    }

    public List<UserResult> parseUsers(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(body, new TypeReference<>() {});
    }

    public HttpCallResult deleteUser(long id) {
        try {
            URI url = base().resolve("api/cdn/admin/users/" + id);

            TransportRequest request = TransportRequest.delete(url, requestTimeout, adminHeaders());

            TransportResponse response = transportClient.send(request);
            return toHttpCallResult(response);
        } catch (Exception ex) {
            return HttpCallResult.ioError(ex.getMessage());
        }
    }

    private Map<String, String> adminHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Admin-Token", adminToken);
        headers.put("X-User-Id", String.valueOf(loggedInUserId));
        return headers;
    }

    private Map<String, String> adminJsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static HttpCallResult toHttpCallResult(TransportResponse response) {
        if (response.error() != null) {
            return HttpCallResult.ioError(response.error());
        }
        return HttpCallResult.http(Objects.requireNonNull(response.statusCode(), "statusCode"), response.body());
    }
}
