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

class AdminRoutingServiceTest {

    private static final String ADMIN_TOKEN = "secret-token";

    @Test
    void addNode_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.addRoutingNode(
                URI.create("http://localhost:8082"), ADMIN_TOKEN, "eu-west", URI.create("http://localhost:8081"));

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/routing?region=eu-west&url=http%3A%2F%2Flocalhost%3A8081",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void removeNode_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.removeRoutingNode(
                URI.create("http://localhost:8082"), ADMIN_TOKEN, "eu-west", URI.create("http://localhost:8081"));

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/routing?region=eu-west&url=http%3A%2F%2Flocalhost%3A8081",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void listNodes_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.listRoutingNodes(URI.create("http://localhost:8082"), ADMIN_TOKEN, true);

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/routing?checkHealth=true",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void bulkUpdate_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpAdminOperations adminOperations = new HttpAdminOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = adminOperations.bulkUpdateRouting(
                URI.create("http://localhost:8082"),
                ADMIN_TOKEN,
                "[{\"region\":\"eu-west\",\"url\":\"http://localhost:8081\",\"action\":\"add\"}]");

        assertEquals(200, result.code());
        assertNotNull(transportClient.lastRequest);
        assertEquals("POST", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/routing/bulk",
                transportClient.lastRequest.uri().toString());
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
