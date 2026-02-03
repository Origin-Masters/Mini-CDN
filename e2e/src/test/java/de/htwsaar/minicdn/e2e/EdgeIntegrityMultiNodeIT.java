package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import de.htwsaar.minicdn.edge.EdgeApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class EdgeIntegrityMultiNodeIT extends AbstractE2E {

    private static final int EDGE2_PORT = 8083;
    private static final int EDGE3_PORT = 8084;

    private static final String EDGE2_BASE = "http://localhost:" + EDGE2_PORT;
    private static final String EDGE3_BASE = "http://localhost:" + EDGE3_PORT;

    private static ConfigurableApplicationContext edge2Ctx;
    private static ConfigurableApplicationContext edge3Ctx;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String SHA_HEADER = "X-Content-SHA256";

    @AfterAll
    static void stopExtraEdges() {
        if (edge2Ctx != null) {
            edge2Ctx.close();
            edge2Ctx = null;
        }
        if (edge3Ctx != null) {
            edge3Ctx.close();
            edge3Ctx = null;
        }
    }

    @Test
    void integrity_is_identical_on_three_edges() throws Exception {

        // 1) zusätzliche Edge-Server mit "edge"-Profil, aber überschriebenen Ports
        edge2Ctx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .properties(
                        "server.port=" + EDGE2_PORT,
                        "origin.base-url=" + ORIGIN_BASE,
                        "edge.cache.ttl-ms=60000",
                        "edge.cache.max-entries=100")
                // Setze als Command-Line-Args mit höchster Priorität
                .build()
                .run("--server.port=" + EDGE2_PORT);

        edge3Ctx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .properties(
                        "server.port=" + EDGE3_PORT,
                        "origin.base-url=" + ORIGIN_BASE,
                        "edge.cache.ttl-ms=60000",
                        "edge.cache.max-entries=100")
                .build()
                .run("--server.port=" + EDGE3_PORT);

        // 2) Testdatei im Origin anlegen
        String fileName = "integrity-" + System.currentTimeMillis() + ".bin";
        URI adminUri = URI.create(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        byte[] payload = new byte[128_000];
        CLIENT.send(
                HttpRequest.newBuilder(adminUri)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        try {
            // 3) SHA direkt vom Origin holen (Referenz)
            String originSha = fetchSha(ORIGIN_BASE + "/api/origin/files/" + fileName);
            assertNotNull(originSha);

            // 4) Datei über ALLE drei Edges abrufen
            String edge1Sha = fetchSha(EDGE_BASE + "/api/edge/files/" + fileName);
            String edge2Sha = fetchSha(EDGE2_BASE + "/api/edge/files/" + fileName);
            String edge3Sha = fetchSha(EDGE3_BASE + "/api/edge/files/" + fileName);

            // 5) Vergleich: alle müssen exakt gleich sein
            assertEquals(originSha, edge1Sha, "Edge #1 liefert veränderte Daten");
            assertEquals(originSha, edge2Sha, "Edge #2 liefert veränderte Daten");
            assertEquals(originSha, edge3Sha, "Edge #3 liefert veränderte Daten");

        } finally {
            // 6) Cleanup im Origin
            CLIENT.send(HttpRequest.newBuilder(adminUri).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        }
    }

    private static String fetchSha(String url) throws Exception {
        HttpResponse<byte[]> resp = CLIENT.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        return resp.headers().firstValue(SHA_HEADER).orElseThrow();
    }
}
