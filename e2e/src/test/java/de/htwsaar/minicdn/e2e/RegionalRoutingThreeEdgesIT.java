package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.edge.EdgeApp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Expliziter Integrationstest fuer US-M3.
 *
 * <p>Der Test startet drei Edge-Server mit unterschiedlichen Regionen und prueft:
 * 1. Jede Edge hat eine konfigurierbare Region.
 * 2. Der Router leitet je nach Client-Region an die passende Edge weiter.
 * 3. Das Ganze laeuft mit mindestens drei Edge-Servern.
 */
class RegionalRoutingThreeEdgesIT extends AbstractE2E {

    private static final String REGION_EU = "eu-west";
    private static final String REGION_US = "us-east";
    private static final String REGION_ASIA = "asia-south";

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static ConfigurableApplicationContext edgeEuCtx;
    private static ConfigurableApplicationContext edgeUsCtx;
    private static ConfigurableApplicationContext edgeAsiaCtx;

    private static String edgeEuBase;
    private static String edgeUsBase;
    private static String edgeAsiaBase;

    /**
     * Raeumt die im Test dynamisch gestarteten Zusatz-Edges wieder auf.
     */
    @AfterAll
    static void stopRegionalEdges() {
        if (edgeEuCtx != null) {
            edgeEuCtx.close();
            edgeEuCtx = null;
        }
        if (edgeUsCtx != null) {
            edgeUsCtx.close();
            edgeUsCtx = null;
        }
        if (edgeAsiaCtx != null) {
            edgeAsiaCtx.close();
            edgeAsiaCtx = null;
        }
    }

    @Test
    void router_routes_clients_to_edge_of_same_region_with_three_edges() throws Exception {
        // Drei echte Edge-Server mit drei verschiedenen Regionen starten.
        // So koennen wir sauber pruefen, ob die Client-Region spaeter wirklich
        // auf die passende Edge derselben Region gemappt wird.
        edgeEuCtx = startEdgeWithRegion(REGION_EU);
        edgeEuBase = "http://localhost:" + localPort(edgeEuCtx);

        edgeUsCtx = startEdgeWithRegion(REGION_US);
        edgeUsBase = "http://localhost:" + localPort(edgeUsCtx);

        edgeAsiaCtx = startEdgeWithRegion(REGION_ASIA);
        edgeAsiaBase = "http://localhost:" + localPort(edgeAsiaCtx);

        String fileName = "regional-routing-" + System.currentTimeMillis() + ".txt";
        URI originAdminUri = URI.create(ORIGIN_BASE + "/api/origin/admin/files/" + fileName);

        try {
            // Testdatei auf dem Origin anlegen, damit der Router spaeter etwas
            // Sinnvolles an die passende Edge weiterleiten kann.
            uploadOriginFile(originAdminUri, "US-M3 Routing Test");

            // Akzeptanzkriterium 1: Edge-Server haben eine konfigurierbare Region.
            assertEquals(REGION_EU, fetchEdgeRegion(edgeEuBase));
            assertEquals(REGION_US, fetchEdgeRegion(edgeUsBase));
            assertEquals(REGION_ASIA, fetchEdgeRegion(edgeAsiaBase));

            // Die drei Edges werden unter ihrer jeweiligen Region beim Router registriert.
            // Der Router weiss danach also, welche Edge zu welcher Region gehoert.
            registerEdge(REGION_EU, edgeEuBase);
            registerEdge(REGION_US, edgeUsBase);
            registerEdge(REGION_ASIA, edgeAsiaBase);

            // Akzeptanzkriterium 2: Client-Anfragen werden an dieselbe Region weitergeleitet.
            assertRedirectForRegion(fileName, REGION_EU, edgeEuBase);
            assertRedirectForRegion(fileName, REGION_US, edgeUsBase);
            assertRedirectForRegion(fileName, REGION_ASIA, edgeAsiaBase);

        } finally {
            unregisterEdge(REGION_EU, edgeEuBase);
            unregisterEdge(REGION_US, edgeUsBase);
            unregisterEdge(REGION_ASIA, edgeAsiaBase);
            deleteOriginFile(originAdminUri);
        }
    }

    /**
     * Startet eine einzelne Edge-Instanz mit einer explizit gesetzten Region.
     *
     * <p>Damit machen wir sichtbar, dass die Region kein harter Wert im Code ist,
     * sondern pro Edge-Server konfigurierbar gesetzt werden kann.</p>
     */
    private static ConfigurableApplicationContext startEdgeWithRegion(String region) {
        String edgeId = region + "-" + System.nanoTime();
        String stateDir = "target/e2e-edge-state-" + edgeId;
        return new SpringApplicationBuilder(EdgeApp.class)
                .profiles("edge")
                .run(
                        "--server.port=0",
                        "--origin.base-url=" + ORIGIN_BASE,
                        "--edge.region=" + region,
                        "--edge.recovery.state-file=" + stateDir + "/edge-runtime-state.properties",
                        "--edge.cache.state-file=" + stateDir + "/edge-cache-state.properties",
                        "--edge.cache.ttl-ms=60000",
                        "--edge.cache.max-entries=100",
                        "--minicdn.admin.token=" + ADMIN_TOKEN);
    }

    /**
     * Legt eine Testdatei auf dem Origin ab.
     *
     * <p>Die Datei wird spaeter ueber den Router angefragt. Dadurch pruefen wir
     * nicht nur Konfiguration, sondern den echten Request-Flow bis zum Redirect.</p>
     */
    private static void uploadOriginFile(URI originAdminUri, String content) throws Exception {
        HttpResponse<Void> response = CLIENT.send(
                HttpRequest.newBuilder(originAdminUri)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .header("Content-Type", "application/octet-stream")
                        .PUT(HttpRequest.BodyPublishers.ofString(content))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertTrue(response.statusCode() == 201 || response.statusCode() == 204);
    }

    /**
     * Entfernt die Testdatei wieder vom Origin.
     */
    private static void deleteOriginFile(URI originAdminUri) throws Exception {
        CLIENT.send(
                HttpRequest.newBuilder(originAdminUri)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Liest die Region einer Edge ueber deren Info-Endpunkt aus.
     *
     * <p>Das ist der direkte Nachweis fuer Akzeptanzkriterium 1 auf Edge-Seite.</p>
     */
    private static String fetchEdgeRegion(String edgeBase) throws Exception {
        HttpResponse<String> response = CLIENT.send(
                HttpRequest.newBuilder(URI.create(edgeBase + "/api/edge/info"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        return body.path("region").asText();
    }

    /**
     * Registriert eine Edge beim Router fuer eine bestimmte Region.
     */
    private static void registerEdge(String region, String edgeBase) throws Exception {
        HttpResponse<Void> response = CLIENT.send(
                HttpRequest.newBuilder(
                                URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + edgeBase))
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(201, response.statusCode());
    }

    /**
     * Entfernt eine Edge wieder aus dem Routing-Index des Routers.
     */
    private static void unregisterEdge(String region, String edgeBase) throws Exception {
        CLIENT.send(
                HttpRequest.newBuilder(
                                URI.create(ROUTER_BASE + "/api/cdn/routing?region=" + region + "&url=" + edgeBase))
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Sendet eine Client-Anfrage mit einer bestimmten Region an den Router
     * und prueft, dass der Redirect zur Edge derselben Region fuehrt.
     *
     * <p>Das ist der direkte Nachweis fuer Akzeptanzkriterium 2.</p>
     */
    private static void assertRedirectForRegion(String fileName, String clientRegion, String expectedEdgeBase)
            throws Exception {
        HttpResponse<Void> response = CLIENT.send(
                HttpRequest.newBuilder(URI.create(ROUTER_BASE + "/api/cdn/files/" + fileName))
                        .header("X-Client-Region", clientRegion)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals(307, response.statusCode());

        String location = response.headers().firstValue("location").orElseThrow();
        assertEquals(expectedEdgeBase + "/api/edge/files/" + fileName, location);
    }
}
