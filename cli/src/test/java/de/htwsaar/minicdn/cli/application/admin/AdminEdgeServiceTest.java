package de.htwsaar.minicdn.cli.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

class AdminEdgeServiceTest {

    private static final String ADMIN_TOKEN = "secret-token";

    @Test
    void startEdge_shouldExtendTimeoutWhenWaitingForReadiness() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(5));

        CallResult result = adminOperations.startEdge(
                URI.create("http://localhost:8082"),
                ADMIN_TOKEN,
                "eu-west",
                10000,
                URI.create("http://localhost:8080"),
                true,
                true);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/edges/activations",
                transportClient.lastRequest.uri().toString());
        assertEquals(Duration.ofSeconds(13), transportClient.lastRequest.timeout());
    }

    @Test
    void startEdgesAuto_shouldScaleTimeoutWithEdgeCountWhenWaitingForReadiness() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(5));

        CallResult result = adminOperations.startEdgesAuto(
                URI.create("http://localhost:8082"),
                ADMIN_TOKEN,
                "us",
                10,
                URI.create("http://localhost:8080"),
                true,
                true);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/edges/activations/automations",
                transportClient.lastRequest.uri().toString());
        assertEquals(Duration.ofSeconds(85), transportClient.lastRequest.timeout());
    }

    @Test
    void startEdgesAuto_shouldKeepDefaultTimeoutWithoutReadinessWait() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(5));

        CallResult result = adminOperations.startEdgesAuto(
                URI.create("http://localhost:8082"),
                ADMIN_TOKEN,
                "us",
                10,
                URI.create("http://localhost:8080"),
                true,
                false);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals(Duration.ofSeconds(5), transportClient.lastRequest.timeout());
    }

    private static final class RecordingTransportClient implements TransportClient {
        private TransportRequest lastRequest;

        @Override
        public TransportResponse send(TransportRequest request) {
            this.lastRequest = request;
            return TransportResponse.success(200, "ok", Map.<String, List<String>>of());
        }

        @Override
        public DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
