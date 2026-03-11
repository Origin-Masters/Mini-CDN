package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CdnStandardFlowIT extends AbstractE2E {

    private static final String REGION = "eu-west";
    private static final String CACHE_HEADER = "X-Cache";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private static final HttpClient NO_REDIRECT_CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void origin_upload_then_delete_works() throws Exception {
        TestFile tf = createOriginFile("Hallo vom Origin");
        try {
            HttpResponse<String> getResp = CLIENT.send(
                    HttpRequest.newBuilder(originPublicFileUri(tf.fileName()))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResp.statusCode());
            assertTrue(getResp.body().contains("Hallo vom Origin"));
        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
        }
    }

    @Test
    void edge_caches_miss_then_hit() throws Exception {
        TestFile tf = createOriginFile("Hallo vom Origin");
        try {
            registerEdgeInRouter();
            URI edgeUri = routeViaRouterExpectRedirectToEdge(tf.fileName());

            assertEdgeGet(edgeUri, "Hallo vom Origin", "MISS");
            assertEdgeGet(edgeUri, "Hallo vom Origin", "HIT");
        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            cleanupRouterEdgeRegistration();
        }
    }

    @Test
    void end_to_end_standard_flow_like_before() throws Exception {
        TestFile tf = createOriginFile("Hallo vom Origin");
        try {
            registerEdgeInRouter();

            URI edgeUri = routeViaRouterExpectRedirectToEdge(tf.fileName());
            assertEdgeGet(edgeUri, "Hallo vom Origin", "MISS");
            assertEdgeGet(edgeUri, "Hallo vom Origin", "HIT");
        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            cleanupRouterEdgeRegistration();
        }
    }

    @Test
    void delivery_guarantee_retry_on_node_failure() throws Exception {
        TestFile tf = createOriginFile("Retry Test Content");

        try {
            registerEdgeInRouter(REGION, "http://localhost:9999");
            registerEdgeInRouter();
            registerEdgeInRouter(REGION, "http://localhost:7777");
            HttpResponse<Void> response = requestRouting(tf.fileName());

            assertEquals(307, response.statusCode());
            String location = response.headers().firstValue("location").orElse("");
            assertTrue(location.startsWith(EDGE_BASE));

        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            unregisterEdge(REGION, "http://localhost:9999");
            unregisterEdge(REGION, EDGE_BASE);
            unregisterEdge(REGION, "http://localhost:7777");
        }
    }
    /**
     * @Test
     * void delivery_guarantee_fails_when_all_nodes_dead() throws Exception {
     * TestFile tf = createOriginFile("Retry Test Content");
     * try {
     * unregisterEdge(REGION, EDGE_BASE);
     * registerEdgeInRouter(REGION, "http://localhost:9998");
     * registerEdgeInRouter(REGION, "http://localhost:9997");
     * registerEdgeInRouter(REGION, "http://localhost:9996");
     * HttpResponse<Void> response = requestRouting(tf.fileName());
     * assertEquals(503, response.statusCode());
     * } finally {
     * cleanupOriginFile(tf.originAdminFileUri());
     * unregisterEdge(REGION, "http://localhost:9998");
     * unregisterEdge(REGION, "http://localhost:9997");
     * unregisterEdge(REGION, "http://localhost:9996");
     * }
     * }
     */
    @Test
    void testParallelRequestStability() throws Exception {
        int numberOfParallelRequests = 10;
        TestFile tf = createOriginFile("Retry Test Content");

        try {

            String testUrl = ROUTER_BASE + "/api/cdn/files/" + tf.fileName + "?region=" + REGION;

            HttpRequest request =
                    HttpRequest.newBuilder().uri(URI.create(testUrl)).build();

            registerEdgeInRouter();

            long startTime = System.nanoTime(); // nanoTime ist für Benchmarks präziser

            // 1. Alle Anfragen asynchron abfeuern
            List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, numberOfParallelRequests)
                    .mapToObj(i -> CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                    .collect(Collectors.toList());

            // 2. Warten, bis ALLE fertig sind
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long endTime = System.nanoTime();

            // Berechnung der Statistiken
            long totalDurationMs = (endTime - startTime) / 1_000_000;
            long avgDurationMs = totalDurationMs / numberOfParallelRequests;

            // Statistische Ausgabe (immer sichtbar im Log)
            System.out.println("--------------------------------------------------");
            System.out.println("BENCHMARK ERGEBNIS:");
            System.out.println("Gesamtdauer: " + totalDurationMs + "ms");
            System.out.println("Durchschnitt pro Request: " + avgDurationMs + "ms");
            System.out.println("--------------------------------------------------");

            // 3. Validierung der Ergebnisse
            String errorMsg = String.format(
                    "Benchmark fehlgeschlagen! Gesamt: %dms, Schnitt: %dms", totalDurationMs, avgDurationMs);

            for (CompletableFuture<HttpResponse<String>> future : futures) {
                int status = future.join().statusCode();
                // Wir prüfen auf 200 OK.
                // Hinweis: Falls die Datei im Test-Setup fehlt, käme 404 zurück.
                assertEquals(200, status, errorMsg + " | Einer der Statuscodes war: " + status);
            }
            //
            System.out.println("NFA-S1 erfüllt: Alle Anfragen erfolgreich verarbeitet.");
        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            unregisterEdge(REGION, EDGE_BASE);
        }
    }

    // NFA-S3 (Zustellgarantie & Origin-Fallback)
    @Test
    @DisplayName("NFA-S3: Zustellgarantie durch Retries und Fallback auf den Origin")
    void delivery_guarantee_retry_and_fallback_to_origin() throws Exception {
        TestFile tf = createOriginFile("Dieser Inhalt kommt im Notfall vom Origin");

        try {
            // Wir registrieren nur "tote" Edges, um den Fehlerfall zu provozieren
            registerEdgeInRouter(REGION, "http://localhost:9991");
            registerEdgeInRouter(REGION, "http://localhost:9992");

            // Wir stellen sicher, dass die echte Edge NICHT registriert ist
            unregisterEdge(REGION, EDGE_BASE);

            // Routing-Anfrage stellen
            HttpResponse<Void> response = requestRouting(tf.fileName());

            // Check 1: Der Router muss trotzdem antworten (Redirect statt Error)
            assertEquals(307, response.statusCode(), "Router sollte bei Edge-Ausfall zum Origin leiten");

            String location = response.headers().firstValue("location").orElse("");

            // Check 2: Die Location muss zum ORIGIN zeigen (Port 8080)
            // Das beweist, dass die Zustellgarantie gegriffen hat
            assertTrue(
                    location.contains(":8080/api/origin/files/"),
                    "Location sollte zum Origin-Server führen. Pfad war: " + location);

            System.out.println("[NFA-S3 Test] Erfolg: Router leitet zum Origin weiter, wenn Edges nicht antworten.");

        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            unregisterEdge(REGION, "http://localhost:9991");
            unregisterEdge(REGION, "http://localhost:9992");
        }
    }

    @Test
    @DisplayName("TS-C2: Ausgefallene Replikate werden automatisch erkannt und entfernt")
    void failover_removes_dead_replica_within_ten_seconds() throws Exception {
        String region = "failover-e2e";
        String deadEdgeUrl = "http://localhost:65534";
        String deadEdgeUrlNormalized = deadEdgeUrl + "/";
        String healthyEdgeUrlNormalized = EDGE_BASE + "/";

        TestFile tf = createOriginFile("Failover E2E Test");

        try {
            // Sauberes Setup: Falls vom letzten Lauf noch Einträge da sind, räumen wir sie weg.
            unregisterEdge(region, deadEdgeUrl);
            unregisterEdge(region, EDGE_BASE);

            // Eine tote und eine funktionierende Edge für dieselbe Region registrieren.
            registerEdgeInRouter(region, deadEdgeUrl);
            registerEdgeInRouter(region, EDGE_BASE);

            long start = System.currentTimeMillis();
            boolean removedInTime = waitUntilEdgeRemoved(region, deadEdgeUrlNormalized, 10_000);
            long durationMs = System.currentTimeMillis() - start;

            assertTrue(removedInTime, "Die tote Edge sollte innerhalb von 10 Sekunden entfernt werden.");
            assertTrue(durationMs <= 10_000, "Die Erkennung dauerte zu lange: " + durationMs + "ms");

            List<String> urlsAfterCleanup = fetchRoutingUrls(region);

            // Nach dem Health-Check darf nur noch die lebende Edge im Routing-Index stehen.
            assertFalse(urlsAfterCleanup.contains(deadEdgeUrlNormalized));
            assertTrue(urlsAfterCleanup.contains(healthyEdgeUrlNormalized));
            assertEquals(1, urlsAfterCleanup.size());

            // Ein echter Request sollte jetzt direkt zur verbleibenden Edge weitergeleitet werden.
            HttpResponse<Void> response = requestRouting(tf.fileName(), region);
            assertEquals(307, response.statusCode());

            String location = response.headers().firstValue("location").orElseThrow();
            assertEquals(EDGE_BASE + "/api/edge/files/" + tf.fileName(), location);

        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            unregisterEdge(region, deadEdgeUrl);
            unregisterEdge(region, EDGE_BASE);
        }
    }

    // ---------- Hilfsmethoden ----------

    private record TestFile(String fileName, URI originAdminFileUri) {}

    private static TestFile createOriginFile(String content) throws Exception {
        String fileName = "test-" + System.currentTimeMillis() + ".txt";
        URI adminUri = uri(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        HttpRequest putReq = HttpRequest.newBuilder(adminUri)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .PUT(HttpRequest.BodyPublishers.ofString(content))
                .header("Content-Type", "application/octet-stream")
                .build();

        HttpResponse<Void> putResp = CLIENT.send(putReq, HttpResponse.BodyHandlers.discarding());
        assertTrue(putResp.statusCode() == 201 || putResp.statusCode() == 204);

        return new TestFile(fileName, adminUri);
    }

    private static URI originPublicFileUri(String fileName) {
        return uri(ORIGIN_BASE + "/api/origin/files/" + fileName);
    }

    private static void registerEdgeInRouter() throws Exception {
        URI addEdgeUri = uri(ROUTER_BASE + "/api/cdn/routing?region=" + REGION + "&url=" + EDGE_BASE);
        HttpRequest addEdgeReq = HttpRequest.newBuilder(addEdgeUri)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> addEdgeResp = NO_REDIRECT_CLIENT.send(addEdgeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(201, addEdgeResp.statusCode());
    }

    private static void registerEdgeInRouter(String region, String url) throws Exception {
        URI uri = URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + url);
        CLIENT.send(
                HttpRequest.newBuilder(uri)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static void cleanupRouterEdgeRegistration() throws Exception {
        URI delEdgeUri = uri(ROUTER_BASE + "/api/cdn/routing?region=" + REGION + "&url=" + EDGE_BASE);
        HttpRequest delReq = HttpRequest.newBuilder(delEdgeUri)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .DELETE()
                .build();

        HttpResponse<Void> delResp = NO_REDIRECT_CLIENT.send(delReq, HttpResponse.BodyHandlers.discarding());
        assertTrue(delResp.statusCode() == 200 || delResp.statusCode() == 404);
    }

    private static URI routeViaRouterExpectRedirectToEdge(String fileName) throws Exception {
        URI routeUri = uri(ROUTER_BASE + "/api/cdn/files/" + fileName + "?region=" + REGION);
        HttpRequest routeReq = HttpRequest.newBuilder(routeUri).GET().build();

        HttpResponse<Void> routeResp = NO_REDIRECT_CLIENT.send(routeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(307, routeResp.statusCode());

        String location = routeResp.headers().firstValue("location").orElseThrow();
        assertEquals(EDGE_BASE + "/api/edge/files/" + fileName, location);

        return uri(location);
    }

    private static void assertEdgeGet(URI edgeFileUri, String expectedBodyContains, String expectedCacheHeader)
            throws Exception {
        HttpRequest edgeReq = HttpRequest.newBuilder(edgeFileUri).GET().build();
        HttpResponse<String> edgeResp = CLIENT.send(edgeReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, edgeResp.statusCode());
        assertTrue(edgeResp.body().contains(expectedBodyContains));
        assertEquals(
                expectedCacheHeader, edgeResp.headers().firstValue(CACHE_HEADER).orElseThrow());
    }

    private static void cleanupOriginFile(URI originAdminFileUri) throws Exception {
        HttpRequest deleteReq = HttpRequest.newBuilder(originAdminFileUri)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .DELETE()
                .build();
        HttpResponse<Void> deleteResp = CLIENT.send(deleteReq, HttpResponse.BodyHandlers.discarding());

        assertTrue(deleteResp.statusCode() == 204 || deleteResp.statusCode() == 404);
    }

    private static URI uri(String s) {
        return URI.create(s);
    }

    private static void unregisterEdge(String region, String url) throws Exception {
        URI uri = URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + url);
        CLIENT.send(
                HttpRequest.newBuilder(uri)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static HttpResponse<Void> requestRouting(String fileName) throws Exception {
        return requestRouting(fileName, REGION);
    }

    private static HttpResponse<Void> requestRouting(String fileName, String region) throws Exception {
        URI routeUri = URI.create(ROUTER_BASE + "/api/cdn/files/" + fileName + "?region=" + region);
        return NO_REDIRECT_CLIENT.send(
                HttpRequest.newBuilder(routeUri).GET().build(), HttpResponse.BodyHandlers.discarding());
    }

    private static boolean waitUntilEdgeRemoved(String region, String edgeUrl, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            List<String> urls = fetchRoutingUrls(region);
            if (!urls.contains(edgeUrl)) {
                return true;
            }
            Thread.sleep(250);
        }

        return false;
    }

    private static List<String> fetchRoutingUrls(String region) throws Exception {
        URI routingUri = URI.create(ROUTER_BASE + "/api/cdn/routing?checkHealth=false");
        HttpRequest request = HttpRequest.newBuilder(routingUri)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode regionNodes = root.path(region);

        List<String> urls = new ArrayList<>();
        if (!regionNodes.isArray()) {
            return urls;
        }

        for (JsonNode node : regionNodes) {
            urls.add(node.path("url").asText());
        }
        return urls;
    }
}
