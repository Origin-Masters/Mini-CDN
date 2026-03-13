package de.htwsaar.minicdn.cli.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
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
        AdminConfigService service = new AdminConfigService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        CallResult result = service.getEdgeTtlPolicies(URI.create("http://localhost:8081"));

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8081/api/edge/admin/config/ttl",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert den PUT-Aufruf zum Setzen einer Prefix-TTL-Policy.
     */
    @Test
    void setEdgeTtlPolicy_shouldSendPutWithJsonPayload() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AdminConfigService service = new AdminConfigService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        CallResult result = service.setEdgeTtlPolicy(URI.create("http://localhost:8081"), "videos/", 15_000L);

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("PUT", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8081/api/edge/admin/config/ttl",
                transportClient.lastRequest.uri().toString());
        assertEquals("{\"prefix\":\"videos/\",\"ttlMs\":15000}", transportClient.lastRequest.body());
    }

    /**
     * Verifiziert den DELETE-Aufruf zum Entfernen einer Prefix-TTL-Policy.
     */
    @Test
    void removeEdgeTtlPolicy_shouldCallDeleteWithEncodedQueryParam() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AdminConfigService service = new AdminConfigService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        CallResult result = service.removeEdgeTtlPolicy(URI.create("http://localhost:8081"), "videos/2026");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8081/api/edge/admin/config/ttl?prefix=videos%2F2026",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert Client-Validierung: ohne Prefix wird vor dem Transport-Aufruf abgebrochen.
     */
    @Test
    void setEdgeTtlPolicy_shouldRejectBlankPrefixBeforeSend() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AdminConfigService service = new AdminConfigService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.setEdgeTtlPolicy(URI.create("http://localhost:8081"), "   ", 5_000L));

        assertEquals("prefix must not be blank", ex.getMessage());
        assertEquals(0, transportClient.sendCalls);
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
