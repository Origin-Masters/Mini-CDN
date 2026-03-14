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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AdminFileService} focusing on correct URL construction,
 * HTTP method selection, header population, and input validation.
 */
class AdminFileServiceTest {

    private static final String ADMIN_TOKEN = "secret-token";
    private static final long USER_ID = 42L;
    private static final URI ROUTER_BASE_URL = URI.create("http://localhost:8080");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private RecordingTransportClient transportClient;
    private AdminFileService service;

    @BeforeEach
    void setUp() {
        transportClient = new RecordingTransportClient();
        service = new AdminFileService(transportClient, TIMEOUT, ROUTER_BASE_URL, ADMIN_TOKEN, USER_ID);
    }

    // -------------------------------------------------------------------------
    // uploadViaRouter
    // -------------------------------------------------------------------------

    @Test
    void uploadViaRouter_shouldSendPutWithFileBodyToCorrectUrl() {
        Path dummyFile = Path.of("manual.pdf");

        CallResult result = service.uploadViaRouter("docs/manual.pdf", dummyFile, "eu");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("PUT", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/files/docs/manual.pdf?region=eu",
                transportClient.lastRequest.uri().toString());
        assertEquals(dummyFile, transportClient.lastRequest.bodyFile());
    }

    @Test
    void uploadViaRouter_shouldIncludeAdminTokenAndUserIdAndContentTypeHeaders() {
        service.uploadViaRouter("docs/manual.pdf", Path.of("manual.pdf"), "eu");

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
        assertEquals("application/octet-stream", headers.get("Content-Type"));
    }

    @Test
    void uploadViaRouter_shouldStripLeadingSlashFromTargetPath() {
        service.uploadViaRouter("/docs/manual.pdf", Path.of("manual.pdf"), "eu");

        assertEquals(
                "http://localhost:8080/api/cdn/admin/files/docs/manual.pdf?region=eu",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void uploadViaRouter_shouldRejectNullLocalFile() {
        assertThrows(NullPointerException.class, () -> service.uploadViaRouter("docs/manual.pdf", null, "eu"));
        assertEquals(0, transportClient.sendCalls);
    }

    @Test
    void uploadViaRouter_shouldRejectBlankTargetPath() {
        assertThrows(IllegalArgumentException.class, () -> service.uploadViaRouter("   ", Path.of("manual.pdf"), "eu"));
        assertEquals(0, transportClient.sendCalls);
    }

    @Test
    void uploadViaRouter_shouldRejectBlankRegion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadViaRouter("docs/manual.pdf", Path.of("manual.pdf"), "   "));
        assertEquals(0, transportClient.sendCalls);
    }

    // -------------------------------------------------------------------------
    // deleteViaRouter
    // -------------------------------------------------------------------------

    @Test
    void deleteViaRouter_shouldSendDeleteToCorrectUrl() {
        CallResult result = service.deleteViaRouter("docs/manual.pdf", "eu");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("DELETE", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/files/docs/manual.pdf?region=eu",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void deleteViaRouter_shouldIncludeAdminTokenAndUserIdHeaders() {
        service.deleteViaRouter("docs/manual.pdf", "eu");

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
    }

    @Test
    void deleteViaRouter_shouldRejectBlankTargetPath() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteViaRouter("   ", "eu"));
        assertEquals(0, transportClient.sendCalls);
    }

    @Test
    void deleteViaRouter_shouldRejectBlankRegion() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteViaRouter("docs/manual.pdf", "   "));
        assertEquals(0, transportClient.sendCalls);
    }

    // -------------------------------------------------------------------------
    // listFilesRaw
    // -------------------------------------------------------------------------

    @Test
    void listFilesRaw_shouldSendGetToFilesEndpoint() {
        CallResult result = service.listFilesRaw();

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/files",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void listFilesRaw_shouldIncludeAdminTokenAndUserIdHeaders() {
        service.listFilesRaw();

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
    }

    // -------------------------------------------------------------------------
    // showViaRouter
    // -------------------------------------------------------------------------

    @Test
    void showViaRouter_shouldSendGetToCorrectUrl() {
        CallResult result = service.showViaRouter("docs/manual.pdf");

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/admin/files/docs/manual.pdf",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void showViaRouter_shouldIncludeAdminTokenAndUserIdHeaders() {
        service.showViaRouter("docs/manual.pdf");

        Map<String, String> headers = transportClient.lastRequest.headers();
        assertEquals(ADMIN_TOKEN, headers.get("X-Admin-Token"));
        assertEquals(String.valueOf(USER_ID), headers.get("X-User-Id"));
    }

    @Test
    void showViaRouter_shouldStripLeadingSlashFromPath() {
        service.showViaRouter("/docs/manual.pdf");

        assertEquals(
                "http://localhost:8080/api/cdn/admin/files/docs/manual.pdf",
                transportClient.lastRequest.uri().toString());
    }

    @Test
    void showViaRouter_shouldRejectBlankPath() {
        assertThrows(IllegalArgumentException.class, () -> service.showViaRouter("   "));
        assertEquals(0, transportClient.sendCalls);
    }

    // -------------------------------------------------------------------------
    // Test double
    // -------------------------------------------------------------------------

    /**
     * Test double for {@link TransportClient} that records the last request for assertions.
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
