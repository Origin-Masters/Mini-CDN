package de.htwsaar.minicdn.common.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests for {@link AdminAuthFilter} behavior on protected and public routes.
 */
class AdminAuthFilterTest {
    /** Valid token used for positive authentication tests. */
    /** Filter under test initialized with the valid token. */
    private static final String VALID_TOKEN = "secret-token";

    private final AdminAuthFilter filter = new AdminAuthFilter(VALID_TOKEN);

    /**
     * Verifies that a missing token on a protected route yields401 and stops the chain.
     */
    @Test
    void shouldReturn401WhenTokenIsMissing() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/origin/admin/files/test.txt"); // Geschützte Route
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest(), "Chain sollte nicht weiterlaufen");
    }

    /**
     * Verifies that an invalid token on a protected route yields403 and stops the chain.
     */
    @Test
    void shouldReturn403WhenTokenIsInvalid() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/origin/admin/files/test.txt");
        req.addHeader("X-Admin-Token", "wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        assertNull(chain.getRequest(), "Chain sollte nicht weiterlaufen");
    }

    /**
     * Verifies that a valid token on a protected route passes the request through the chain.
     */
    @Test
    void shouldPassThroughWhenTokenIsCorrect() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/origin/admin/files/test.txt");
        req.addHeader("X-Admin-Token", VALID_TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest(), "Chain sollte weiterlaufen");
    }

    /**
     * Verifies that a public route does not require a token and passes through the chain.
     */
    @Test
    void shouldPassThroughForPublicUserRoute() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/edge/files/test.txt"); // Öffentliche Route
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest(), "User sollte ohne Token durchkommen");
    }

    /**
     * Verifies that a blank token on an admin route yields401 and stops the chain.
     */
    @Test
    void shouldReturn401WhenTokenIsBlank() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/cdn/routing");
        req.addHeader("X-Admin-Token", "   ");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest());
    }
}
