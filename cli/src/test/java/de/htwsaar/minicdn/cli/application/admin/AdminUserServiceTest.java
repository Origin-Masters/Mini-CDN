package de.htwsaar.minicdn.cli.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import de.htwsaar.minicdn.cli.adapter.out.http.HttpAdminOperations;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.model.UserResult;
import de.htwsaar.minicdn.common.serialization.JacksonCodec;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests für {@link AdminUserService} mit Fokus auf korrekte URL-Bildung,
 * HTTP-Methode, Header-Setzung, Input-Validierung und JSON-Parsing.
 */
class AdminUserServiceTest {

    private static final String ADMIN_TOKEN = "secret-token";
    private static final long USER_ID = 42L;
    private static final URI ROUTER_BASE_URL = URI.create("http://localhost:8080");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private RecordingTransportClient transportClient;
    private AdminUserService service;
    private HttpAdminOperations adminOperations;

    @BeforeEach
    void setUp() {
        transportClient = new RecordingTransportClient();
        adminOperations = new HttpAdminOperations(transportClient, TIMEOUT);
        service = new AdminUserService(adminOperations, ROUTER_BASE_URL, ADMIN_TOKEN, USER_ID);
    }

    // -------------------------------------------------------------------------
    // addUser
    // -------------------------------------------------------------------------

    @Test
    void addUser_shouldSendPostJsonToUsersEndpointWithHeadersAndTrimmedName() {
        CallResult result = adminOperations.addUser(ROUTER_BASE_URL, ADMIN_TOKEN, USER_ID, "alice", 1);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/users",
                transportClient.lastRequest.uri().toString());

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
        assertEquals("application/json", headers.get("Content-Type"));

        assertJsonEquals(Map.of("name", "alice", "role", 1), transportClient.lastRequest.body());
    }

    @Test
    void addUser_shouldRejectBlankNameBeforeSend() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.addUser("   ", 1));

        assertEquals("name must not be blank", ex.getMessage());
        assertEquals(0, transportClient.sendCalls);
    }

    // -------------------------------------------------------------------------
    // listUsersRaw
    // -------------------------------------------------------------------------

    @Test
    void listUsersRaw_shouldSendGetToUsersEndpointWithAdminHeaders() {
        CallResult result = adminOperations.listUsers(ROUTER_BASE_URL, ADMIN_TOKEN, USER_ID);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/users",
                transportClient.lastRequest.uri().toString());

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
    }

    // -------------------------------------------------------------------------
    // parseUsers
    // -------------------------------------------------------------------------

    @Test
    void parseUsers_shouldReturnEmptyListForNullOrBlankBody() {
        assertTrue(service.parseUsers(null).isEmpty());
        assertTrue(service.parseUsers("").isEmpty());
        assertTrue(service.parseUsers("   ").isEmpty());
    }

    @Test
    void parseUsers_shouldParseValidJsonArray() {
        String body =
                """
                [
                  {"id":1,"name":"alice","role":1},
                  {"id":2,"name":"bob","role":0}
                ]
                """;

        List<UserResult> users = service.parseUsers(body);

        assertEquals(2, users.size());
        assertEquals(new UserResult(1L, "alice", 1), users.get(0));
        assertEquals(new UserResult(2L, "bob", 0), users.get(1));
    }

    @Test
    void parseUsers_shouldThrowIllegalArgumentExceptionOnInvalidJson() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.parseUsers("{not-valid-json"));

        assertEquals("failed to parse users JSON", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    // -------------------------------------------------------------------------
    // deleteUser
    // -------------------------------------------------------------------------

    @Test
    void deleteUser_shouldRejectNonPositiveIdWithoutTransportCall() {
        CallResult result = service.deleteUser(0);

        assertEquals(400, result.code());
        assertEquals("id must be greater than 0", result.error());
        assertEquals(0, transportClient.sendCalls);
    }

    @Test
    void deleteUser_shouldSendDeleteToExpectedEndpointWithAdminHeaders() {
        CallResult result = adminOperations.deleteUser(ROUTER_BASE_URL, ADMIN_TOKEN, USER_ID, 7L);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/users/7",
                transportClient.lastRequest.uri().toString());

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
    }

    /**
     * Vergleicht JSON semantisch über Map-Parsen statt String-Gleichheit.
     */
    private static void assertJsonEquals(Map<String, Object> expected, String actualJson) {
        try {
            Map<String, Object> actual = JacksonCodec.fromJson(actualJson, new TypeReference<>() {});
            assertEquals(expected, actual);
        } catch (Exception ex) {
            throw new AssertionError("JSON assertion failed: " + ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Test-Doppel
    // -------------------------------------------------------------------------

    /**
     * Test-Doppel für {@link TransportClient}, das den letzten Request speichert.
     */
    private static final class RecordingTransportClient implements TransportClient {

        int sendCalls;
        TransportRequest lastRequest;

        @Override
        public TransportResponse send(TransportRequest request) {
            this.sendCalls++;
            this.lastRequest = request;
            return TransportResponse.success(200, "ok", Map.<String, List<String>>of());
        }

        @Override
        public DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
