package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CdnStandardFlowIT extends AbstractE2E {

    private static final String REGION = "eu-west";
    private static final String CACHE_HEADER = "X-Cache";

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

    @Test
    void delivery_guarantee_fails_when_all_nodes_dead() throws Exception {
        TestFile tf = createOriginFile("Retry Test Content");
        try {
            unregisterEdge(REGION, EDGE_BASE);
            registerEdgeInRouter(REGION, "http://localhost:9998");
            registerEdgeInRouter(REGION, "http://localhost:9997");
            registerEdgeInRouter(REGION, "http://localhost:9996");
            HttpResponse<Void> response = requestRouting(tf.fileName());
            assertEquals(503, response.statusCode());
        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            unregisterEdge(REGION, "http://localhost:9998");
            unregisterEdge(REGION, "http://localhost:9997");
            unregisterEdge(REGION, "http://localhost:9996");
        }
    }

    // ---------- Hilfsmethoden ----------

    private record TestFile(String fileName, URI originAdminFileUri) {}

    private static TestFile createOriginFile(String content) throws Exception {
        String fileName = "test-" + System.currentTimeMillis() + ".txt";
        URI adminUri = uri(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        HttpRequest putReq = HttpRequest.newBuilder(adminUri)
                .header("X-Admin-Token", "secret-token")
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
                .header("X-Admin-Token", "secret-token")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> addEdgeResp = NO_REDIRECT_CLIENT.send(addEdgeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(201, addEdgeResp.statusCode());
    }

    private static void registerEdgeInRouter(String region, String url) throws Exception {
        URI uri = URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + url);
        CLIENT.send(
                HttpRequest.newBuilder(uri)
                        .header("X-Admin-Token", "secret-token")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static void cleanupRouterEdgeRegistration() throws Exception {
        URI delEdgeUri = uri(ROUTER_BASE + "/api/cdn/routing?region=" + REGION + "&url=" + EDGE_BASE);
        HttpRequest delReq = HttpRequest.newBuilder(delEdgeUri)
                .header("X-Admin-Token", "secret-token")
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
                .header("X-Admin-Token", "secret-token")
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
                        .header("X-Admin-Token", "secret-token")
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static HttpResponse<Void> requestRouting(String fileName) throws Exception {
        URI routeUri = URI.create(ROUTER_BASE + "/api/cdn/files/" + fileName + "?region=" + REGION);
        return NO_REDIRECT_CLIENT.send(
                HttpRequest.newBuilder(routeUri).GET().build(), HttpResponse.BodyHandlers.discarding());
    }

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
}
