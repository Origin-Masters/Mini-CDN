package de.htwsaar.minicdn.cli.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Contract-Tests fuer die URL-Bildung in {@link UserStatsService}.
 */
class UserStatsServiceTest {

    /**
     * Verifiziert den Datei-Statistik-Endpunkt fuer eine konkrete Datei-ID.
     */
    @Test
    void fileStatsForCurrentUser_shouldCallUserStatsFileEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        UserStatsService service = new UserStatsService(
                transportClient, Duration.ofSeconds(2), URI.create("http://localhost:8082"), () -> 17L);

        CallResult result = service.fileStatsForCurrentUser(123);

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals("17", transportClient.lastRequest.headers().get("X-User-Id"));
        assertEquals(
                "http://localhost:8082/api/cdn/stats/file/123",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert den Listen-Endpunkt inklusive Limit-Parameter.
     */
    @Test
    void listUserFilesStats_shouldCallUserStatsFilesEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        UserStatsService service = new UserStatsService(
                transportClient, Duration.ofSeconds(2), URI.create("http://localhost:8082/"), () -> 17L);

        CallResult result = service.listUserFilesStats(10);

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals("17", transportClient.lastRequest.headers().get("X-User-Id"));
        assertEquals(
                "http://localhost:8082/api/cdn/stats/files?limit=10",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert den Gesamtstatistik-Endpunkt inklusive Zeitfenster.
     */
    @Test
    void overallStatsForCurrentUser_shouldCallUserStatsEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        UserStatsService service = new UserStatsService(
                transportClient, Duration.ofSeconds(2), URI.create("http://localhost:8082"), () -> 17L);

        CallResult result = service.overallStatsForCurrentUser(3600);

        assertEquals(200, result.statusCode());
        assertNotNull(transportClient.lastRequest);
        assertEquals("GET", transportClient.lastRequest.method());
        assertEquals("17", transportClient.lastRequest.headers().get("X-User-Id"));
        assertEquals(
                "http://localhost:8082/api/cdn/stats?windowSec=3600",
                transportClient.lastRequest.uri().toString());
    }

    /**
     * Verifiziert, dass die User-ID pro Request neu aus dem Supplier gelesen wird.
     */
    @Test
    void listUserFilesStats_shouldUseLatestUserIdFromSupplier() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        AtomicLong userId = new AtomicLong(2L);
        UserStatsService service = new UserStatsService(
                transportClient, Duration.ofSeconds(2), URI.create("http://localhost:8082"), userId::get);

        CallResult first = service.listUserFilesStats(5);
        assertEquals(200, first.statusCode());
        assertEquals("2", transportClient.lastRequest.headers().get("X-User-Id"));

        userId.set(7L);
        CallResult second = service.listUserFilesStats(5);
        assertEquals(200, second.statusCode());
        assertEquals("7", transportClient.lastRequest.headers().get("X-User-Id"));
    }

    /**
     * Verifiziert den Guard-Fall ohne eingeloggten User.
     */
    @Test
    void overallStatsForCurrentUser_shouldFailWhenUserIdMissing() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        UserStatsService service = new UserStatsService(
                transportClient, Duration.ofSeconds(2), URI.create("http://localhost:8082"), () -> -1L);

        CallResult result = service.overallStatsForCurrentUser(60);

        assertEquals("login required: missing user id", result.error());
    }

    /**
     * Einfaches Transport-Testdouble, das den letzten Request speichert.
     */
    private static final class RecordingTransportClient implements TransportClient {
        private TransportRequest lastRequest;

        @Override
        public TransportResponse send(TransportRequest request) {
            this.lastRequest = request;
            return TransportResponse.success(200, "ok", Map.of());
        }

        @Override
        public DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
