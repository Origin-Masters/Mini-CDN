package de.htwsaar.minicdn.cli.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.htwsaar.minicdn.cli.adapter.out.http.HttpAdminOperations;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests für {@link AdminConfigService} mit Fokus auf TTL-Policy-Administration via Admin-CLI.
 */
class AdminConfigServiceTest {

    private static final String ADMIN_TOKEN = "secret-token";

    /**
     * Verifiziert den GET-Aufruf für TTL-Policies.
     */
    @Test
    void getEdgeTtlPolicies_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.getEdgeTtlPolicies(URI.create("http://localhost:8081"), ADMIN_TOKEN);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8081/api/edge/admin/configs/expirations",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert den PUT-Aufruf zum Setzen einer Prefix-TTL-Policy.
     */
    @Test
    void setEdgeTtlPolicy_shouldSendPutWithJsonPayload() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result =
                adminOperations.setEdgeTtlPolicy(URI.create("http://localhost:8081"), ADMIN_TOKEN, "videos/", 15_000L);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("PUT", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8081/api/edge/admin/configs/expirations",
                transportClient.lastRequest.uri().toString());
        assertEquals("{\"prefix\":\"videos/\",\"ttlMs\":15000}", transportClient.lastRequest.body());
    }

    /**
     * Verifiziert den DELETE-Aufruf zum Entfernen einer Prefix-TTL-Policy.
     */
    @Test
    void removeEdgeTtlPolicy_shouldCallDeleteWithEncodedQueryParam() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result =
                adminOperations.removeEdgeTtlPolicy(URI.create("http://localhost:8081"), ADMIN_TOKEN, "videos/2026");

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8081/api/edge/admin/configs/expirations?prefix=videos%2F2026",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert Client-Validierung: ohne Prefix wird vor dem Transport-Aufruf abgebrochen.
     */
    @Test
    void setEdgeTtlPolicy_shouldRejectBlankPrefixBeforeSend() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminOperations.setEdgeTtlPolicy(
                        URI.create("http://localhost:8081"), ADMIN_TOKEN, "   ", 5_000L));

        assertEquals("prefix must not be blank", ex.getMessage());
        assertEquals(0, transportClient.sendCalls);
    }

    @Test
    void getOriginCluster_shouldCallRouterEndpointWithHealthFlag() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.getOriginCluster(URI.create("http://localhost:8082"), ADMIN_TOKEN, true);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/origins/clusters?checkHealth=true",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void addOriginSpare_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.addOriginSpare(
                URI.create("http://localhost:8082"), ADMIN_TOKEN, URI.create("http://localhost:8084"));

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/origins/spares?url=http%3A%2F%2Flocalhost%3A8084",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void promoteOriginSpare_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.promoteOriginSpare(
                URI.create("http://localhost:8082"), ADMIN_TOKEN, URI.create("http://localhost:8084"));

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/origins/promotions?url=http%3A%2F%2Flocalhost%3A8084",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void checkOriginFailover_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.checkOriginFailover(URI.create("http://localhost:8082"), ADMIN_TOKEN);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/origins/failovers/checks",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Test-Doppel für {@link TransportClient}, das den letzten Request für Assertions erfasst.
     */
    private static final class RecordingTransportClient implements TransportClient {
        private int sendCalls;
        private TransportRequest lastRequest;

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
