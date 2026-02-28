package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CDNControllerTest extends AbstractE2E {

    private final RestTemplate restTemplate = createAuthenticatedTemplate();
    /**
     * Builds a RestTemplate with an interceptor that ensures the `X-Admin-Token` header is present.
     * If the header is missing, a default token is added before the request is executed.
     *
     *
     */
    private RestTemplate createAuthenticatedTemplate() {
        RestTemplate template = new RestTemplate();
        template.getInterceptors().add((request, body, execution) -> {
            if (!request.getHeaders().containsKey("X-Admin-Token")) {
                request.getHeaders().add("X-Admin-Token", ADMIN_TOKEN);
            }
            return execution.execute(request, body);
        });
        return template;
    }

    @Test
    @Order(1)
    @DisplayName("E2E: Health-Checks aller Komponenten")
    void testHealthOfAllSystems() {
        assertEquals("ok", restTemplate.getForObject(ROUTER_BASE + "/api/cdn/health", String.class));
        assertEquals("ok", restTemplate.getForObject(EDGE_BASE + "/api/edge/health", String.class));
        // Falls Origin einen Health-Check hat:
        // assertEquals("ok", restTemplate.getForObject(ORIGIN_BASE + "/api/origin/health", String.class));
    }

    @Test
    @Order(2)
    @DisplayName("E2E: Registrierung einer Edge-Node und anschließendes Routing")
    void testRegistrationAndRouting() {
        String registrationUrl = ROUTER_BASE + "/api/cdn/routing?region=EU&url=" + EDGE_BASE;
        ResponseEntity<Void> regResponse = restTemplate.postForEntity(registrationUrl, null, Void.class);
        assertEquals(HttpStatus.CREATED, regResponse.getStatusCode());

        String routerFileUrl = ROUTER_BASE + "/api/cdn/files/test.txt?region=EU";

        ResponseEntity<String> routeResponse = awaitRoutingOk(routerFileUrl, Duration.ofSeconds(5));

        assertEquals(HttpStatus.OK, routeResponse.getStatusCode());
    }

    private ResponseEntity<String> awaitRoutingOk(String url, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        HttpServerErrorException last = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                return restTemplate.getForEntity(url, String.class);
            } catch (HttpServerErrorException e) {
                last = e;
                if (e.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                    throw e;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
        throw last != null ? last : new IllegalStateException("Routing did not succeed in time");
    }

    @Test
    @Order(3)
    @DisplayName("E2E: Bulk-Update von mehreren Nodes")
    void testBulkUpdateIntegration() {
        String bulkUrl = ROUTER_BASE + "/api/cdn/routing/bulk";
        String jsonPayload =
                """
            [
                {"region": "US", "url": "http://localhost:9001", "action": "add"},
                {"region": "US", "url": "http://localhost:9002", "action": "add"}
            ]
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        ResponseEntity<List> response = restTemplate.postForEntity(bulkUrl, entity, List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    @Order(4)
    @DisplayName("E2E: Metriken prüfen nach Traffic")
    void testMetricsIntegration() {
        String metricsUrl = ROUTER_BASE + "/api/cdn/routing/metrics";

        // Metriken abrufen
        Map<String, Object> metrics = restTemplate.getForObject(metricsUrl, Map.class);

        assertNotNull(metrics);
        assertTrue(metrics.containsKey("totalRequests"));
        // Da vorherige Tests bereits Anfragen gemacht haben, sollte der Counter > 0 sein
        Integer totalRequests = (Integer) metrics.get("totalRequests");
        assertTrue(totalRequests > 0);
    }

    @Test
    @Order(5)
    @DisplayName("E2E: Fehlerbehandlung wenn Region fehlt")
    void testMissingRegionError() {
        String errorUrl = ROUTER_BASE + "/api/cdn/files/some-file.jpg"; // Ohne ?region=...

        try {
            restTemplate.getForEntity(errorUrl, String.class);
            fail("Sollte einen 400 Bad Request werfen");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertTrue(e.getResponseBodyAsString().contains("Region fehlt"));
        }
    }
}
