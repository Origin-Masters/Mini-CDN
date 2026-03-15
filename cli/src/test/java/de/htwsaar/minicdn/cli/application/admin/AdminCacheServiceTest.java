package de.htwsaar.minicdn.cli.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.model.TransportRequest;
import de.htwsaar.minicdn.cli.domain.model.TransportResponse;
import de.htwsaar.minicdn.cli.domain.port.TransportClient;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminCacheServiceTest {

    private static final String ADMIN_TOKEN = "secret-token";

    @Test
    void invalidateFile_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AdminCacheService service = new AdminCacheService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        CallResult result = service.invalidateFile(URI.create("http://localhost:8082"), "eu-west", "videos/intro.mp4");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/cache/region/eu-west/files/videos/intro.mp4",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void invalidatePrefix_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AdminCacheService service = new AdminCacheService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        CallResult result = service.invalidatePrefix(URI.create("http://localhost:8082"), "eu-west", "videos/2026");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/cache/region/eu-west/prefix?value=videos%2F2026",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void clearRegion_shouldCallExpectedEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AdminCacheService service = new AdminCacheService(transportClient, Duration.ofSeconds(2), ADMIN_TOKEN);

        CallResult result = service.clearRegion(URI.create("http://localhost:8082"), "eu-west");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8082/api/cdn/admin/cache/region/eu-west/all",
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
