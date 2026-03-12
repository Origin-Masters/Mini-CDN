package de.htwsaar.minicdn.router.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP-Interceptor zur automatischen Audit-Erfassung von User-Aktionen.
 */
@Component
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AuditLogService auditLogService;

    public AuditLoggingInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Schreibt nach Abschluss der Request-Verarbeitung einen Audit-Eintrag,
     * sofern ein gültiger User-Kontext vorhanden ist.
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long userId = parseUserId(request.getHeader(USER_ID_HEADER));
        if (userId <= 0) {
            return;
        }

        String action = request.getMethod() + " " + request.getRequestURI();
        String resource = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            resource = resource + "?" + request.getQueryString();
        }

        int status = ex == null ? response.getStatus() : 500;
        auditLogService.append(userId, action, resource, status);
    }

    /**
     * Parst die User-ID robust aus Headern.
     */
    private static long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
