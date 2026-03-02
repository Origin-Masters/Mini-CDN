package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CDNControllerTest extends AbstractE2E {

    private final RestTemplate restTemplate = createAuthenticatedTemplate();

    /**
     * Builds a RestTemplate with an interceptor that ensures the `X-Admin-Token` header is present.
     * If the header is missing, a default token is added before the request is executed.
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
    /**
     * @Test
     * @Order(1)
     * @DisplayName("E2E: Health-Checks aller Komponenten")
     * void testHealthOfAllSystems() {
     * assertEquals("ok", restTemplate.getForObject(ROUTER_BASE + "/api/cdn/health", String.class));
     * assertEquals("ok", restTemplate.getForObject(EDGE_BASE + "/api/edge/health", String.class));
     * // Falls Origin einen Health-Check hat:
     * // assertEquals("ok", restTemplate.getForObject(ORIGIN_BASE + "/api/origin/health", String.class));
     * }
     *
     * @Test
     * @Order(2)
     * @DisplayName("E2E: Registrierung einer Edge-Node und anschließendes Routing")
     * void testRegistrationAndRouting() {
     * String registrationUrl = ROUTER_BASE + "/api/cdn/routing?region=EU&url=" + EDGE_BASE;
     * ResponseEntity<Void> regResponse = restTemplate.postForEntity(registrationUrl, null, Void.class);
     * assertEquals(HttpStatus.CREATED, regResponse.getStatusCode());
     *
     * String routerFileUrl = ROUTER_BASE + "/api/cdn/files/test.txt?region=EU";
     *
     * ResponseEntity<String> routeResponse = awaitRoutingOk(routerFileUrl, Duration.ofSeconds(5));
     *
     * assertEquals(HttpStatus.OK, routeResponse.getStatusCode());
     * }
     *
     * private ResponseEntity<String> awaitRoutingOk(String url, Duration timeout) {
     * Instant deadline = Instant.now().plus(timeout);
     * HttpServerErrorException last = null;
     *
     * while (Instant.now().isBefore(deadline)) {
     * try {
     * return restTemplate.getForEntity(url, String.class);
     * } catch (HttpServerErrorException e) {
     * last = e;
     * if (e.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
     * throw e;
     * }
     * try {
     * Thread.sleep(100);
     * } catch (InterruptedException ie) {
     * Thread.currentThread().interrupt();
     * throw new IllegalStateException(ie);
     * }
     * }
     * }
     * throw last != null ? last : new IllegalStateException("Routing did not succeed in time");
     * }
     *
     * @Test
     * @Order(3)
     * @DisplayName("E2E: Bulk-Update von mehreren Nodes")
     * void testBulkUpdateIntegration() {
     * String bulkUrl = ROUTER_BASE + "/api/cdn/routing/bulk";
     * String jsonPayload =
     * """
     * [
     * {"region": "US", "url": "http://localhost:9001", "action": "add"},
     * {"region": "US", "url": "http://localhost:9002", "action": "add"}
     * ]
     * """;
     *
     * HttpHeaders headers = new HttpHeaders();
     * headers.setContentType(MediaType.APPLICATION_JSON);
     * HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
     *
     * ResponseEntity<List> response = restTemplate.postForEntity(bulkUrl, entity, List.class);
     *
     * assertEquals(HttpStatus.OK, response.getStatusCode());
     * assertNotNull(response.getBody());
     * assertEquals(2, response.getBody().size());
     * }
     *
     * @Test
     * @Order(4)
     * @DisplayName("E2E: Router-Statistiken prüfen nach Traffic")
     * @SuppressWarnings("unchecked")
     * void testAdminStatsIntegration() {
     * String statsUrl = ROUTER_BASE + "/api/cdn/admin/stats?windowSec=60&aggregateEdge=false";
     *
     * Map<String, Object> stats = restTemplate.getForObject(statsUrl, Map.class);
     *
     * assertNotNull(stats);
     * assertTrue(stats.containsKey("timestamp"));
     * assertTrue(stats.containsKey("windowSec"));
     * assertTrue(stats.containsKey("router"));
     * assertTrue(stats.containsKey("downloads"));
     * assertTrue(stats.containsKey("nodes"));
     * assertTrue(stats.containsKey("edgeAggregation"));
     *
     * Map<String, Object> router = (Map<String, Object>) stats.get("router");
     * assertNotNull(router);
     * assertTrue(router.containsKey("totalRequests"));
     * assertTrue(router.containsKey("routingErrors"));
     * assertTrue(router.containsKey("requestsByRegion"));
     *
     * Number totalRequests = (Number) router.get("totalRequests");
     * assertNotNull(totalRequests);
     * assertTrue(totalRequests.longValue() > 0, "Es sollten bereits Requests gezählt worden sein");
     *
     * Map<String, Object> requestsByRegion = (Map<String, Object>) router.get("requestsByRegion");
     * assertNotNull(requestsByRegion);
     * assertTrue(requestsByRegion.containsKey("EU"), "Die Region EU sollte nach dem Routing-Test vorhanden sein");
     *
     * Map<String, Object> edgeAggregation = (Map<String, Object>) stats.get("edgeAggregation");
     * assertNotNull(edgeAggregation);
     * assertEquals(Boolean.FALSE, edgeAggregation.get("enabled"));
     * }
     *
     * @Test
     * @Order(5)
     * @DisplayName("E2E: Fehlerbehandlung wenn Region fehlt")
     * void testMissingRegionError() {
     * String errorUrl = ROUTER_BASE + "/api/cdn/files/some-file.jpg"; // Ohne ?region=...
     *
     * try {
     * restTemplate.getForEntity(errorUrl, String.class);
     * fail("Sollte einen 400 Bad Request werfen");
     * } catch (org.springframework.web.client.HttpClientErrorException e) {
     * assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
     * assertTrue(e.getResponseBodyAsString().contains("Region fehlt"));
     * }
     * }
     */
}
