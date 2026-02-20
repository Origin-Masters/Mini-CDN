package de.htwsaar.minicdn.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminSecurityTest extends AbstractE2E {

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    @Order(1)
    @DisplayName("Security: Negativtest - 401 Unauthorized bei fehlendem Token")
    void testMissingTokenReturns401() {
        String adminUrl = ROUTER_BASE + "/api/cdn/admin/stats";

        try {
            // Wir senden absichtlich KEINEN Header mit
            restTemplate.getForEntity(adminUrl, String.class);
            fail("Sollte 401 Unauthorized werfen");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Security: Negativtest - 403 Forbidden bei falschem Token")
    void testInvalidTokenReturns403() {
        String adminUrl = ROUTER_BASE + "/api/cdn/admin/stats";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "wrong-token-123");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Wir senden absichtlich ein FALSCHES Token mit
            restTemplate.exchange(adminUrl, HttpMethod.GET, entity, String.class);
            fail("Sollte 403 Forbidden werfen");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    @Order(3)
    @DisplayName("Security: Positivtest - 200 OK bei korrektem Token")
    void testValidTokenReturns200() {
        String adminUrl = ROUTER_BASE + "/api/cdn/admin/stats";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "secret-token");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // sending the correct token
        ResponseEntity<String> response = restTemplate.exchange(adminUrl, HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
