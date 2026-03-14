package de.htwsaar.minicdn.cli.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests für {@link UserFileService} mit Fokus auf korrekte URL-Bildung,
 * Header-Setzung, Input-Validierung, Fehlerabbildung und Hilfslogik.
 */
class UserFileServiceTest {

    private static final URI ROUTER_BASE_URL = URI.create("http://localhost:8080");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private RecordingTransportClient transportClient;
    private UserFileService service;

    @BeforeEach
    void setUp() {
        transportClient = new RecordingTransportClient();
        service = new UserFileService(transportClient, TIMEOUT);
    }

    // -------------------------------------------------------------------------
    // downloadViaRouter (ohne userId)
    // -------------------------------------------------------------------------

    @Test
    void downloadViaRouter_shouldSendGetToCorrectUrlAndForwardTargetArguments() {
        Path out = Path.of("download.bin");

        DownloadResult result =
                service.downloadViaRouter(ROUTER_BASE_URL, "/docs/manual.pdf", "eu", "client-1", out, true);

        assertEquals(200, result.statusCode());
        assertEquals(123L, result.bytesWritten());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals(
                "http://localhost:8080/api/cdn/files/docs/manual.pdf",
                transportClient.lastRequest.uri().toString());

        assertEquals("eu", transportClient.lastRequest.headers().get("X-Client-Region"));
        assertEquals("client-1", transportClient.lastRequest.headers().get("X-Client-Id"));
        assertFalse(transportClient.lastRequest.headers().containsKey("X-User-Id"));

        assertEquals(out, transportClient.lastTargetFile);
        assertEquals(true, transportClient.lastOverwrite);
    }

    @Test
    void downloadViaRouter_shouldTrimRegionAndClientId() {
        service.downloadViaRouter(
                ROUTER_BASE_URL, "docs/manual.pdf", "  eu  ", "  client-1  ", Path.of("x.bin"), false);

        assertEquals("eu", transportClient.lastRequest.headers().get("X-Client-Region"));
        assertEquals("client-1", transportClient.lastRequest.headers().get("X-Client-Id"));
    }

    @Test
    void downloadViaRouter_shouldNotSendClientIdHeaderWhenBlank() {
        service.downloadViaRouter(ROUTER_BASE_URL, "docs/manual.pdf", "eu", "   ", Path.of("x.bin"), false);

        assertFalse(transportClient.lastRequest.headers().containsKey("X-Client-Id"));
    }

    // -------------------------------------------------------------------------
    // downloadViaRouter (mit userId)
    // -------------------------------------------------------------------------

    @Test
    void downloadViaRouter_withUserId_shouldIncludeUserIdHeaderWhenPositive() {
        service.downloadViaRouter(ROUTER_BASE_URL, "docs/manual.pdf", "eu", "client-1", 17L, Path.of("x.bin"), false);

        assertEquals("17", transportClient.lastRequest.headers().get("X-User-Id"));
    }

    @Test
    void downloadViaRouter_withUserId_shouldSkipUserIdHeaderWhenNullOrNonPositive() {
        service.downloadViaRouter(ROUTER_BASE_URL, "docs/manual.pdf", "eu", "client-1", null, Path.of("a.bin"), false);
        assertFalse(transportClient.lastRequest.headers().containsKey("X-User-Id"));

        service.downloadViaRouter(ROUTER_BASE_URL, "docs/manual.pdf", "eu", "client-1", 0L, Path.of("b.bin"), false);
        assertFalse(transportClient.lastRequest.headers().containsKey("X-User-Id"));
    }

    // -------------------------------------------------------------------------
    // Validierung
    // -------------------------------------------------------------------------

    @Test
    void downloadViaRouter_shouldRejectBlankRemotePath() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.downloadViaRouter(ROUTER_BASE_URL, "   ", "eu", "client-1", Path.of("x.bin"), false));
        assertEquals(0, transportClient.downloadCalls);
    }

    @Test
    void downloadViaRouter_shouldRejectBlankRegion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.downloadViaRouter(
                        ROUTER_BASE_URL, "docs/manual.pdf", "   ", "client-1", Path.of("x.bin"), false));
        assertEquals(0, transportClient.downloadCalls);
    }

    @Test
    void downloadViaRouter_shouldRejectNullOutFile() {
        assertThrows(
                NullPointerException.class,
                () -> service.downloadViaRouter(ROUTER_BASE_URL, "docs/manual.pdf", "eu", "client-1", null, false));
        assertEquals(0, transportClient.downloadCalls);
    }

    // -------------------------------------------------------------------------
    // Fehlerabbildung
    // -------------------------------------------------------------------------

    @Test
    void downloadViaRouter_shouldMapTransportExceptionToIoErrorResult() {
        transportClient.throwOnDownload = new RuntimeException("boom");

        DownloadResult result = service.downloadViaRouter(
                ROUTER_BASE_URL, "docs/manual.pdf", "eu", "client-1", Path.of("x.bin"), false);

        assertEquals(null, result.statusCode());
        assertEquals(0L, result.bytesWritten());
        assertEquals("boom", result.error());
    }

    // -------------------------------------------------------------------------
    // splitIntoSegments – Hilfslogik
    // -------------------------------------------------------------------------

    /**
     * Prüft die gleichmäßige Segmentierung inklusive Restverteilung.
     */
    @Test
    void splitIntoSegments_distributesRemainder() {
        List<UserFileService.SegmentPlan> plans = UserFileService.splitIntoSegments(10, 3);

        assertEquals(3, plans.size());
        assertEquals(0, plans.get(0).start());
        assertEquals(3, plans.get(0).end());
        assertEquals(4, plans.get(1).start());
        assertEquals(6, plans.get(1).end());
        assertEquals(7, plans.get(2).start());
        assertEquals(9, plans.get(2).end());
    }

    /**
     * Prüft die Eingabevalidierung für ungültige Dateigrößen.
     */
    @Test
    void splitIntoSegments_rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> UserFileService.splitIntoSegments(0, 2));
    }

    // -------------------------------------------------------------------------
    // Test-Doppel
    // -------------------------------------------------------------------------

    /**
     * Test-Doppel für {@link TransportClient}, das Download-Aufrufe protokolliert.
     */
    private static final class RecordingTransportClient implements TransportClient {

        int downloadCalls;
        TransportRequest lastRequest;
        Path lastTargetFile;
        boolean lastOverwrite;
        RuntimeException throwOnDownload;
        DownloadResult nextResult = DownloadResult.ok(200, 123L);

        @Override
        public TransportResponse send(TransportRequest request) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite) {
            this.downloadCalls++;
            this.lastRequest = request;
            this.lastTargetFile = targetFile;
            this.lastOverwrite = overwrite;

            if (throwOnDownload != null) {
                throw throwOnDownload;
            }
            return nextResult;
        }
    }
}
