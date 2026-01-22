package de.htwsaar.minicdn.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet-Filter zur Erzeugung und Verwaltung einer Trace-ID.
 * Für jede eingehende HTTP-Anfrage wird eine Trace-ID erzeugt oder aus einem
 * Request-Header übernommen und im MDC abgelegt, sodass sie automatisch
 * in allen Logeinträgen enthalten ist.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** Schlüsselname der Trace-ID im Logging-Kontext */
    public static final String TRACE_ID_KEY = "traceId";

    /** HTTP-Header, aus dem eine vorhandene Trace-ID gelesen werden kann */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Trace-ID aus Header lesen oder neu erzeugen
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Trace-ID im Logging-Kontext ablegen
        MDC.put(TRACE_ID_KEY, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Wichtig: Kontext nach der Anfrage wieder entfernen
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
