package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.UserResult;
import de.htwsaar.minicdn.router.service.RouterUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autorisiert Admin-Endpunkte zusätzlich über den eingeloggten User.
 *
 * <p>Zusätzlich zum bestehenden Token-Schutz muss ein gültiger User mit Admin-Rolle
 * ({@code role == 1}) über den Header {@code X-User-Id} mitgegeben werden.</p>
 */
public class AdminRoleAuthorizationFilter extends OncePerRequestFilter {
    // Aktuell nicht als Spring-Bean registriert; Vorbereitung für spätere serverseitige Rollenprüfung.

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final int ADMIN_ROLE = 1;

    private final RouterUserService userService;

    public AdminRoleAuthorizationFilter(RouterUserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isAdminRoute(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authenticated user");
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid user id format");
            return;
        }

        Optional<UserResult> user = userService.findUserById(userId);
        if (user.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unknown user");
            return;
        }

        if (user.get().role() != ADMIN_ROLE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "User is not an admin");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAdminRoute(String requestUri) {
        return requestUri != null
                && (requestUri.startsWith("/api/cdn/admin/") || requestUri.startsWith("/api/cdn/routing"));
    }
}
