package de.htwsaar.minicdn.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that protects admin and management endpoints by validating a shared admin token.
 */
public class AdminAuthFilter extends OncePerRequestFilter {

    /** Name of the HTTP header expected to carry the admin token. */
    private static final String AUTH_HEADER = "X-Admin-Token";

    /** Token value configured on the server that incoming admin requests must match. */
    private final String expectedToken;

    /**
     * Creates a new admin authentication filter.
     *
     * @param expectedToken the token that must match the value provided in the admin header */
    public AdminAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    /**
     * Checks incoming requests to admin-related routes for the presence and validity of the admin token.
     * If the token is missing, responds with401 Unauthorized; if invalid, responds with403 Forbidden.
     *
     * @param request the HTTP request * @param response the HTTP response * @param filterChain the remaining filter chain * @throws ServletException if filtering fails * @throws IOException if an I/O error occurs */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // Scope: Alle Admin- und Verwaltungs-Routen von Origin, Edge und Router
        boolean isAdminRoute = requestUri.contains("/admin/")
                || requestUri.contains("/api/cdn/routing")
                || requestUri.contains("/api/edge/cache");

        if (isAdminRoute) {
            String providedToken = request.getHeader(AUTH_HEADER);

            if (providedToken == null || providedToken.isBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Admin Token");
                return;
            }

            if (!expectedToken.equals(providedToken)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Admin Token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
