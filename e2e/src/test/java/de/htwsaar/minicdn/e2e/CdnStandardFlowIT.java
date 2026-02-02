package de.htwsaar.minicdn.e2e;

    import static org.junit.jupiter.api.Assertions.*;

    import de.htwsaar.minicdn.edge.EdgeApp;
    import de.htwsaar.minicdn.origin.OriginApp;
    import de.htwsaar.minicdn.router.RouterApp;
    import java.net.URI;
    import java.net.http.HttpClient;
    import java.net.http.HttpRequest;
    import java.net.http.HttpResponse;
    import org.junit.jupiter.api.AfterAll;
    import org.junit.jupiter.api.BeforeAll;
    import org.junit.jupiter.api.Test;
    import org.springframework.boot.builder.SpringApplicationBuilder;
    import org.springframework.context.ConfigurableApplicationContext;

    /**
     * End-to-End-Integrationstest für den Standard-Flow des CDN-Systems.
     * Startet Origin-, Edge- und Router-Server und testet die Interaktion zwischen den Komponenten.
     */
    class CdnStandardFlowIT {

        private static final int ORIGIN_PORT = 8080;
        private static final int EDGE_PORT = 8081;
        private static final int ROUTER_PORT = 8082;

        private static final String ORIGIN_BASE = "http://localhost:" + ORIGIN_PORT;
        private static final String EDGE_BASE = "http://localhost:" + EDGE_PORT;
        private static final String ROUTER_BASE = "http://localhost:" + ROUTER_PORT;

        private static final String REGION = "eu-west";
        private static final String CACHE_HEADER = "X-Cache";

        private static final HttpClient CLIENT = HttpClient.newHttpClient();
        private static final HttpClient NO_REDIRECT_CLIENT = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        private static ConfigurableApplicationContext originCtx;
        private static ConfigurableApplicationContext edgeCtx;
        private static ConfigurableApplicationContext routerCtx;

        /**
         * Startet alle drei Server-Anwendungen vor den Tests.
         */
        @BeforeAll
        static void startApps() {
            originCtx = new SpringApplicationBuilder(OriginApp.class)
                    .profiles("origin")
                    .properties("server.port=" + ORIGIN_PORT)
                    .run();

            edgeCtx = new SpringApplicationBuilder(EdgeApp.class)
                    .profiles("edge")
                    .properties(
                            "server.port=" + EDGE_PORT,
                            "origin.base-url=" + ORIGIN_BASE,
                            "edge.cache.ttl-ms=60000",
                            "edge.cache.max-entries=100"
                    )
                    .run();

            routerCtx = new SpringApplicationBuilder(RouterApp.class)
                    .profiles("cdn")
                    .properties("server.port=" + ROUTER_PORT)
                    .run();
        }

        /**
         * Stoppt alle Server-Anwendungen nach den Tests.
         */
        @AfterAll
        static void stopApps() {
            if (routerCtx != null) routerCtx.close();
            if (edgeCtx != null) edgeCtx.close();
            if (originCtx != null) originCtx.close();
        }

        /**
         * Testet, ob das Hochladen und anschließende Löschen einer Datei am Origin-Server funktioniert.
         */
        @Test
        void origin_upload_then_delete_works() throws Exception {
            TestFile tf = createOriginFile("Hallo vom Origin");
            try {
                HttpResponse<String> getResp = CLIENT.send(
                        HttpRequest.newBuilder(originPublicFileUri(tf.fileName())).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                assertEquals(200, getResp.statusCode());
                assertTrue(getResp.body().contains("Hallo vom Origin"));
            } finally {
                cleanupOriginFile(tf.originAdminFileUri());
            }
        }

        /**
         * Testet, ob der Router korrekt mit HTTP 307 zum Edge-Server umleitet.
         */
        @Test
        void router_redirects_to_edge_with_307_and_location() throws Exception {
            TestFile tf = createOriginFile("Hallo vom Origin");
            try {
                registerEdgeInRouter();
                URI edgeUri = routeViaRouterExpectRedirectToEdge(tf.fileName());
                assertNotNull(edgeUri);
            } finally {
                cleanupOriginFile(tf.originAdminFileUri());
                cleanupRouterEdgeRegistration();
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

        // ---------- Hilfsmethoden ----------

        /**
         * Record für Testdatei-Informationen.
         *
         * @param fileName           der Dateiname
         * @param originAdminFileUri die URI für Admin-Operationen am Origin-Server
         */
        private record TestFile(String fileName, URI originAdminFileUri) {}

        /**
         * Erstellt eine Testdatei am Origin-Server.
         *
         * @param content der Inhalt der Datei
         * @return die Testdatei-Informationen
         */
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

        /**
         * Baut die öffentliche URI für eine Datei am Origin-Server.
         *
         * @param fileName der Dateiname
         * @return die URI für den öffentlichen Zugriff
         */
        private static URI originPublicFileUri(String fileName) {
            return uri(ORIGIN_BASE + "/api/origin/files/" + fileName);
        }

        /**
         * Registriert den Edge-Server im Router für die angegebene Region.
         */
        private static void registerEdgeInRouter() throws Exception {
            URI addEdgeUri = uri(ROUTER_BASE + "/api/cdn/routing?region=" + REGION + "&url=" + EDGE_BASE);
            HttpRequest addEdgeReq = HttpRequest.newBuilder(addEdgeUri)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> addEdgeResp = NO_REDIRECT_CLIENT.send(addEdgeReq, HttpResponse.BodyHandlers.discarding());
            assertEquals(201, addEdgeResp.statusCode());
        }

        /**
         * Entfernt die Edge-Server-Registrierung aus dem Router.
         */
        private static void cleanupRouterEdgeRegistration() throws Exception {
            URI delEdgeUri = uri(ROUTER_BASE + "/api/cdn/routing?region=" + REGION + "&url=" + EDGE_BASE);
            HttpRequest delReq = HttpRequest.newBuilder(delEdgeUri).DELETE().build();

            HttpResponse<Void> delResp = NO_REDIRECT_CLIENT.send(delReq, HttpResponse.BodyHandlers.discarding());
            assertTrue(delResp.statusCode() == 200 || delResp.statusCode() == 404);
        }

        /**
         * Routet eine Anfrage über den Router und erwartet eine Umleitung zum Edge-Server.
         *
         * @param fileName der Dateiname
         * @return die URI des Edge-Servers für die Datei
         */
        private static URI routeViaRouterExpectRedirectToEdge(String fileName) throws Exception {
            URI routeUri = uri(ROUTER_BASE + "/api/cdn/files/" + fileName + "?region=" + REGION);
            HttpRequest routeReq = HttpRequest.newBuilder(routeUri).GET().build();

            HttpResponse<Void> routeResp = NO_REDIRECT_CLIENT.send(routeReq, HttpResponse.BodyHandlers.discarding());
            assertEquals(307, routeResp.statusCode());

            String location = routeResp.headers().firstValue("location").orElseThrow();
            assertEquals(EDGE_BASE + "/api/edge/files/" + fileName, location);

            return uri(location);
        }

        /**
         * Führt einen GET-Request zum Edge-Server aus und überprüft Inhalt und Cache-Status.
         *
         * @param edgeFileUri          die URI der Datei am Edge-Server
         * @param expectedBodyContains der erwartete Inhalt im Body
         * @param expectedCacheHeader  der erwartete Cache-Status (HIT oder MISS)
         */
        private static void assertEdgeGet(URI edgeFileUri, String expectedBodyContains, String expectedCacheHeader)
                throws Exception {
            HttpRequest edgeReq = HttpRequest.newBuilder(edgeFileUri).GET().build();
            HttpResponse<String> edgeResp = CLIENT.send(edgeReq, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, edgeResp.statusCode());
            assertTrue(edgeResp.body().contains(expectedBodyContains));
            assertEquals(expectedCacheHeader, edgeResp.headers().firstValue(CACHE_HEADER).orElseThrow());
        }

        /**
         * Löscht eine Testdatei vom Origin-Server.
         *
         * @param originAdminFileUri die Admin-URI der Datei
         */
        private static void cleanupOriginFile(URI originAdminFileUri) throws Exception {
            HttpRequest deleteReq = HttpRequest.newBuilder(originAdminFileUri).DELETE().build();
            HttpResponse<Void> deleteResp = CLIENT.send(deleteReq, HttpResponse.BodyHandlers.discarding());

            assertTrue(deleteResp.statusCode() == 204 || deleteResp.statusCode() == 404);
        }

        /**
         * Erstellt eine URI aus einem String.
         *
         * @param s der URI-String
         * @return die URI
         */
        private static URI uri(String s) {
            return URI.create(s);
        }
    }