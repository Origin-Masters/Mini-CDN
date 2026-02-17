package de.htwsaar.minicdn.router;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.service.EdgeHttpClient;
import de.htwsaar.minicdn.router.service.MetricsService;
import de.htwsaar.minicdn.router.service.RouterStatsService;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import de.htwsaar.minicdn.router.web.AdminStatsController;
import de.htwsaar.minicdn.router.web.CdnProbeController;
import de.htwsaar.minicdn.router.web.CdnRoutingController;
import de.htwsaar.minicdn.router.web.RoutingAdminController;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CDNControllerTest {

    private MockMvc mockMvc;

    private RoutingIndex routingIndex;
    private MetricsService metricsService;
    private RouterStatsService routerStatsService;

    private EdgeHttpClient edgeHttpClient; // wird gemockt, damit keine echten HTTP-Calls passieren
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        routingIndex = new RoutingIndex();
        metricsService = new MetricsService();
        routerStatsService = new RouterStatsService();
        objectMapper = new ObjectMapper();

        edgeHttpClient = mock(EdgeHttpClient.class);
        when(edgeHttpClient.isNodeResponsive(
                        org.mockito.ArgumentMatchers.any(EdgeNode.class),
                        org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(true);

        var probeController = new CdnProbeController();
        var routingController = new CdnRoutingController(
                routingIndex,
                metricsService,
                routerStatsService,
                edgeHttpClient,
                500, // ackTimeoutMs
                3, // maxRetries
                0 // retryIntervalMs (0 damit Tests nicht schlafen)
                );
        var routingAdminController = new RoutingAdminController(routingIndex, metricsService, edgeHttpClient);
        var adminStatsController =
                new AdminStatsController(routerStatsService, routingIndex, edgeHttpClient, objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        probeController, routingController, routingAdminController, adminStatsController)
                .build();
    }

    @Test
    @DisplayName("Prüfen, ob die Basis-Gesundheits-Check-Endpunkte antworten")
    void testHealthAndReady() throws Exception {
        mockMvc.perform(get("/api/cdn/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        mockMvc.perform(get("/api/cdn/ready"))
                .andExpect(status().isOk())
                .andExpect(content().string("ready"));
    }

    @Test
    @DisplayName("Fehlerfall: Routing ohne Angabe einer Region")
    void testRouteWithoutRegion() throws Exception {
        mockMvc.perform(get("/api/cdn/files/mein-bild.jpg"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Region fehlt")));
    }

    @Test
    @DisplayName("Erfolgsfall: Eine Datei anfragen und zur Edge-Node weitergeleitet werden")
    void testSuccessfulRouting() throws Exception {
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://edge-server-1.com"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cdn/files/video.mp4").param("region", "EU"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "http://edge-server-1.com/api/edge/files/video.mp4"));
    }

    @Test
    @DisplayName("Lastverteilung: Round-Robin soll zwischen zwei Nodes abwechseln")
    void testRoundRobinRouting() throws Exception {
        mockMvc.perform(post("/api/cdn/routing").param("region", "US").param("url", "http://node-A.com"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cdn/routing").param("region", "US").param("url", "http://node-B.com"))
                .andExpect(status().isCreated());

        String ersteLocation = mockMvc.perform(get("/api/cdn/files/test").param("region", "US"))
                .andExpect(status().isTemporaryRedirect())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get("/api/cdn/files/test").param("region", "US"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", not(ersteLocation)));
    }

    @Test
    @DisplayName("Bulk-Update: Mehrere Nodes gleichzeitig über JSON hinzufügen")
    void testBulkUpdate() throws Exception {
        String jsonAnfrage =
                """
            [
                {"region": "DE", "url": "http://node-deutschland.com", "action": "add"},
                {"region": "FR", "url": "http://node-frankreich.com", "action": "add"}
            ]
            """;

        mockMvc.perform(post("/api/cdn/routing/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonAnfrage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status", is("added")));
    }

    @Test
    @DisplayName("Metriken: Zähler müssen sich bei Anfragen erhöhen")
    void testMetrics() throws Exception {
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://metrics-edge.com"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cdn/files/datei.txt").param("region", "EU"))
                .andExpect(status().isTemporaryRedirect());

        mockMvc.perform(get("/api/cdn/routing/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", is(1)))
                .andExpect(jsonPath("$.requestsByRegion.EU", is(1)));
    }

    @Test
    @DisplayName("Admin-Stats: exakte RPM und aktive Clients müssen stimmen")
    void testAdminStatsRpmAndClients() throws Exception {
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://node-eu-1.com"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cdn/files/a.txt").param("region", "EU").param("clientId", "alice"))
                .andExpect(status().isTemporaryRedirect());
        mockMvc.perform(get("/api/cdn/files/b.txt").param("region", "EU").param("clientId", "bob"))
                .andExpect(status().isTemporaryRedirect());
        mockMvc.perform(get("/api/cdn/files/c.txt").param("region", "EU").param("clientId", "alice"))
                .andExpect(status().isTemporaryRedirect());

        mockMvc.perform(get("/api/cdn/admin/stats").param("windowSec", "60").param("aggregateEdge", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.router.totalRequests", is(3)))
                .andExpect(jsonPath("$.router.requestsPerMinute", is(3)))
                .andExpect(jsonPath("$.router.activeClients", is(2)))
                .andExpect(jsonPath("$.router.requestsByRegion.EU", is(3)))
                .andExpect(jsonPath("$.nodes.total", is(1)));
    }

    @Test
    @DisplayName("Entfernen: Eine Node löschen und danach muss Routing fehlschlagen (keine Nodes)")
    void testDeleteNode() throws Exception {
        mockMvc.perform(post("/api/cdn/routing").param("region", "EU").param("url", "http://weg-mit-mir.com"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/cdn/routing").param("region", "EU").param("url", "http://weg-mit-mir.com"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cdn/files/test").param("region", "EU"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("Keine erreichbaren Knoten")));
    }
}
