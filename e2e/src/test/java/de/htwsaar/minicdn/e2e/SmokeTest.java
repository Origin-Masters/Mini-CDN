package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * End-to-End Smoke-Test für den Datei-Download über den Edge-Server mit Zeitmessung.
 *
 * <p>Dieser Test dient als automatisierter Nachweis des Akzeptanzkriteriums:
 * "Download großer Dateien innerhalb einer vorgegebenen Zeit".</p>
 *
 * <p>Ablauf:</p>
 * <ol>
 *   <li>Erzeugt eine größere Testdatei im Origin.</li>
 *   <li>Lädt die Datei über den Edge herunter und misst die benötigte Zeit.</li>
 *   <li>Validiert Statuscode, Dateigröße und maximale Download-Dauer.</li>
 *   <li>Löscht die Testdatei wieder aus dem Origin.</li>
 * </ol>
 *
 * <p>Der Test schlägt fehl, wenn:</p>
 * <ul>
 *   <li>der HTTP-Status ungleich 200 ist,</li>
 *   <li>die Datei kleiner als {@link #MIN_SIZE} ist oder</li>
 *   <li>die Download-Zeit {@link #MAX_TIME_MS} überschreitet.</li>
 * </ul>
 */
class SmokeTest extends AbstractE2E {

    /** Minimale erwartete Dateigröße in Bytes. */
    private static final int MIN_SIZE = 100_000;

    /** Maximale erlaubte Download-Zeit in Millisekunden. */
    private static final long MAX_TIME_MS = 2_000;

    /** Gemeinsamer HTTP-Client für alle Requests dieses Tests. */
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    /**
     * Führt einen Smoke-Test für den Datei-Download mit Zeitmessung durch.
     *
     * <p>Der Test legt zunächst eine Datei im Origin an, lädt diese anschließend
     * über den Edge-Server herunter und misst dabei die benötigte Zeit.</p>
     *
     * <p>Abschließend wird überprüft:</p>
     * <ul>
     *   <li>dass der HTTP-Status 200 ist,</li>
     *   <li>dass mindestens {@link #MIN_SIZE} Bytes übertragen wurden und</li>
     *   <li>dass der Download schneller als {@link #MAX_TIME_MS} erfolgt.</li>
     * </ul>
     *
     * <p>Die erzeugte Datei wird unabhängig vom Testergebnis wieder gelöscht.</p>
     *
     * @throws Exception falls Netzwerkfehler oder unerwartete Probleme auftreten
     */
    @Test
    void smoke_test_download_with_timing() throws Exception {

        // 1) große Datei im Origin anlegen
        String fileName = "smoke-test.txt";
        URI adminUri = URI.create(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        byte[] data = new byte[150_000];

        CLIENT.send(
                HttpRequest.newBuilder(adminUri)
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
            CLIENT.send(HttpRequest.newBuilder(adminUri).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        }
    }
}
