package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * End-to-End-Integrationstest für den Standard-Flow des CDN-Systems.
 * Startet Origin-, Edge- und Router-Server zentral über AbstractE2E.
 */
class CdnStandardFlowIT extends AbstractE2E {

    private static final String REGION = "eu-west";
    private static final String CACHE_HEADER = "X-Cache";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final HttpClient NO_REDIRECT_CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /**
     * Testet, ob das Hochladen und anschließende Löschen einer Datei am Origin-Server funktioniert.
     */
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

    /**
     * Testet, ob der Edge-Cache korrekt funktioniert (MISS beim ersten Request, HIT beim zweiten).
     */
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

    /**
     * Vollständiger End-to-End-Test des Standard-Flows: Upload, Routing, Caching.
     */
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

    /**
     * NFA-S3 Zustellgarantie (Fault-Injection)
     * Router muss einen toten Knoten überspringen und den nächsten funktionierenden wählen.
     */
    @Test
    void delivery_guarantee_retry_on_node_failure() throws Exception {
        TestFile tf = createOriginFile("Retry Test Content");

        try {
            // Erst einen toten Port registrieren, dann die echte Edge
            registerEdgeInRouter(REGION, "http://localhost:9999");
            registerEdgeInRouter(REGION, EDGE_BASE);
            registerEdgeInRouter(REGION, "http://localhost:7777");

            HttpResponse<Void> response = requestRouting(tf.fileName());

            assertEquals(307, response.statusCode(), "Router muss trotz totem Knoten erfolgreich umleiten");
            String location = response.headers().firstValue("location").orElse("");
            assertTrue(location.startsWith(EDGE_BASE), "Redirect muss auf die funktionierende Node zeigen");

            // Metadaten prüfen (NFA-S3 Aufgabe 1)
            assertTrue(response.headers().firstValue("X-CDN-Message-ID").isPresent(), "Message-ID fehlt");
            assertTrue(response.headers().firstValue("X-CDN-Retry-Count").isPresent(), "Retry-Count fehlt");

        } finally {
            cleanupOriginFile(tf.originAdminFileUri());
            unregisterEdge(REGION, "http://localhost:9999");
            unregisterEdge(REGION, EDGE_BASE);
        }
    }

    /**
     *  NFA-S3 Abbruchbedingung
     * Wenn gar kein Knoten antwortet, muss ein Fehler (503) kommen.
     */
    @Test
    void delivery_guarantee_fails_when_all_nodes_dead() throws Exception {
        try {
            registerEdgeInRouter(REGION, "http://localhost:9998");
            registerEdgeInRouter(REGION, "http://localhost:9997");
            registerEdgeInRouter(REGION, "http://localhost:9996");
            HttpResponse<Void> response = requestRouting("any-file.txt");
            assertEquals(503, response.statusCode(), "Sollte 503 liefern, wenn kein Knoten ein Ack sendet");
        } finally {
            unregisterEdge(REGION, "http://localhost:9998");
        }
    }

    // ---------- Hilfsmethoden ----------

    private record TestFile(String fileName, URI originAdminFileUri) {}

    private static TestFile createOriginFile(String content) throws Exception {
        String fileName = "test-" + System.currentTimeMillis() + ".txt";
        URI adminUri = uri(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        HttpRequest putReq = HttpRequest.newBuilder(adminUri)
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
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> addEdgeResp = NO_REDIRECT_CLIENT.send(addEdgeReq, HttpResponse.BodyHandlers.discarding());
        assertEquals(201, addEdgeResp.statusCode());
    }

    private static void registerEdgeInRouter(String region, String url) throws Exception {
        URI uri = URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + url);
        CLIENT.send(
                HttpRequest.newBuilder(uri)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static void cleanupRouterEdgeRegistration() throws Exception {
        URI delEdgeUri = uri(ROUTER_BASE + "/api/cdn/routing?region=" + REGION + "&url=" + EDGE_BASE);
        HttpRequest delReq = HttpRequest.newBuilder(delEdgeUri).DELETE().build();

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
        HttpRequest deleteReq =
                HttpRequest.newBuilder(originAdminFileUri).DELETE().build();
        HttpResponse<Void> deleteResp = CLIENT.send(deleteReq, HttpResponse.BodyHandlers.discarding());

        assertTrue(deleteResp.statusCode() == 204 || deleteResp.statusCode() == 404);
    }

    private static URI uri(String s) {
        return URI.create(s);
    }

    private static void unregisterEdge(String region, String url) throws Exception {
        URI uri = URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + url);
        CLIENT.send(HttpRequest.newBuilder(uri).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    }

    private static HttpResponse<Void> requestRouting(String fileName) throws Exception {
        URI routeUri = URI.create(ROUTER_BASE + "/api/cdn/files/" + fileName + "?region=" + REGION);
        return NO_REDIRECT_CLIENT.send(
                HttpRequest.newBuilder(routeUri).GET().build(), HttpResponse.BodyHandlers.discarding());
    }
}
