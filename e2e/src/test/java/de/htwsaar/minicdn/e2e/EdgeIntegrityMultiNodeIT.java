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

    private static String edge2Base;
    private static String edge3Base;

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

        edge2Ctx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .run(
                        "--server.port=0",
                        "--origin.base-url=" + ORIGIN_BASE,
                        "--edge.cache.ttl-ms=60000",
                        "--edge.cache.max-entries=100",
                        "--minicdn.admin.token=" + ADMIN_TOKEN);
        edge2Base = "http://localhost:" + localPort(edge2Ctx);

        edge3Ctx = new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .run(
                        "--server.port=0",
                        "--origin.base-url=" + ORIGIN_BASE,
                        "--edge.cache.ttl-ms=60000",
                        "--edge.cache.max-entries=100",
                        "--minicdn.admin.token=" + ADMIN_TOKEN);
        edge3Base = "http://localhost:" + localPort(edge3Ctx);

        String fileName = "integrity-" + System.currentTimeMillis() + ".bin";
        URI adminUri = URI.create(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        byte[] payload = new byte[128_000];
        CLIENT.send(
                HttpRequest.newBuilder(adminUri)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        try {
            String originSha = fetchSha(ORIGIN_BASE + "/api/origin/files/" + fileName);
            assertNotNull(originSha);

            String edge1Sha = fetchSha(EDGE_BASE + "/api/edge/files/" + fileName);
            String edge2Sha = fetchSha(edge2Base + "/api/edge/files/" + fileName);
            String edge3Sha = fetchSha(edge3Base + "/api/edge/files/" + fileName);

            assertEquals(originSha, edge1Sha);
            assertEquals(originSha, edge2Sha);
            assertEquals(originSha, edge3Sha);

        } finally {
            CLIENT.send(
                    HttpRequest.newBuilder(adminUri)
                            .header("X-Admin-Token", ADMIN_TOKEN)
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }

    private static String fetchSha(String url) throws Exception {
        HttpResponse<byte[]> resp = CLIENT.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        return resp.headers().firstValue(SHA_HEADER).orElseThrow();
    }
}
