package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class SmokeTest extends AbstractE2E {

    private static final int MIN_SIZE = 100_000;
    private static final long MAX_TIME_MS = 2_000;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Test
    void smoke_test_download_with_timing() throws Exception {

        // 1) große Datei im Origin anlegen
        String fileName = "smoke-test.txt";
        URI adminUri = URI.create(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        byte[] data = new byte[150_000];

        // HIER WAR DER FEHLER: Das Token muss "secret-token" sein!
        CLIENT.send(
                HttpRequest.newBuilder(adminUri)
                        .header("X-Admin-Token", "secret-token")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                        .header("Content-Type", "application/octet-stream")
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        try {
            // 2) Download über Edge mit Zeitmessung
            String url = EDGE_BASE + "/api/edge/files/" + fileName;

            long start = System.currentTimeMillis();

            HttpResponse<byte[]> response = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

            long timeMs = System.currentTimeMillis() - start;
            int size = response.body().length;

            System.out.println("Downloaded bytes: " + size);
            System.out.println("Time: " + timeMs + " ms");

            assertEquals(200, response.statusCode());
            assertTrue(size >= MIN_SIZE, "File too small");
            assertTrue(timeMs < MAX_TIME_MS, "Too slow");

        } finally {
            // 3) Datei wieder löschen
            // AUCH HIER: Token auf "secret-token" anpassen!
            CLIENT.send(
                    HttpRequest.newBuilder(adminUri)
                            .header("X-Admin-Token", "secret-token")
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }
}
