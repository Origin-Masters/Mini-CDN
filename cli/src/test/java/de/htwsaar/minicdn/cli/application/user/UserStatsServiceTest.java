package de.htwsaar.minicdn.cli.application.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.htwsaar.minicdn.cli.adapter.out.http.HttpUserOperations;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.DownloadResult;
import de.htwsaar.minicdn.cli.domain.port.UserOperations;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Contract-Tests für die URL-Bildung in {@link UserStatsService}.
 */
class UserStatsServiceTest {

    /**
     * Verifiziert den Datei-Statistik-Endpunkt für eine konkrete Datei-ID.
     */
    @Test
    void fileStatsForCurrentUser_shouldCallUserStatsFileEndpoint() {
        RecordingTransportClient transportClient = new RecordingTransportClient();
        HttpUserOperations userOperations = new HttpUserOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = userOperations.fileStats(URI.create("http://localhost:8082"), 17L, 123);

        assertEquals(200, result.code());
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
        HttpUserOperations userOperations = new HttpUserOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = userOperations.listFileStats(URI.create("http://localhost:8082/"), 17L, 10);

        assertEquals(200, result.code());
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
        HttpUserOperations userOperations = new HttpUserOperations(transportClient, Duration.ofSeconds(2));

        CallResult result = userOperations.overallStats(URI.create("http://localhost:8082"), 17L, 3600);

        assertEquals(200, result.code());
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
        AtomicLong userId = new AtomicLong(2L);
        RecordingUserOperations userOperations = new RecordingUserOperations();
        UserStatsService service =
                new UserStatsService(userOperations, URI.create("http://localhost:8082"), userId::get);

        CallResult first = service.listUserFilesStats(5);
        assertEquals(200, first.code());
        assertEquals(2L, userOperations.lastLoggedInUserId);

        userId.set(7L);
        CallResult second = service.listUserFilesStats(5);
        assertEquals(200, second.code());
        assertEquals(7L, userOperations.lastLoggedInUserId);
    }

    /**
     * Verifiziert den Guard-Fall ohne eingeloggten User.
     */
    @Test
    void overallStatsForCurrentUser_shouldFailWhenUserIdMissing() {
        UserStatsService service =
                new UserStatsService(new RecordingUserOperations(), URI.create("http://localhost:8082"), () -> -1L);

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

    private static final class RecordingUserOperations implements UserOperations {
        private long lastLoggedInUserId;

        @Override
        public de.htwsaar.minicdn.cli.domain.model.LoginResult login(URI routerBaseUrl, String username) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public CallResult fileStats(URI routerBaseUrl, long loggedInUserId, long fileId) {
            this.lastLoggedInUserId = loggedInUserId;
            return CallResult.success(200, "ok");
        }

        @Override
        public CallResult listFileStats(URI routerBaseUrl, long loggedInUserId, int limit) {
            this.lastLoggedInUserId = loggedInUserId;
            return CallResult.success(200, "ok");
        }

        @Override
        public CallResult overallStats(URI routerBaseUrl, long loggedInUserId, int windowSec) {
            this.lastLoggedInUserId = loggedInUserId;
            return CallResult.success(200, "ok");
        }
    }
}
