package de.htwsaar.minicdn.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Ausführliche Unit-Tests für {@link TraceIdFilter}.
 *
 * <p>Die Tests decken sowohl Normalfälle als auch Randfälle ab und sichern damit die
 * Ticket-Anforderungen zur Trace-ID-Korrelation explizit ab.</p>
 */
class TraceIdFilterTest {

    @AfterEach
    void cleanupMdcAfterTest() {
        MDC.clear();
    }

    /**
     * Wenn ein Client bereits eine Trace-ID mitsendet, muss sie unverändert genutzt werden.
     */
    @Test
    void shouldReuseIncomingTraceIdFromHeader() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "test-trace-123");

        FilterChain chain = (req, resp) -> assertEquals("test-trace-123", MDC.get(TraceIdFilter.TRACE_ID_KEY));

        filter.doFilter(request, response, chain);

        assertEquals("test-trace-123", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertMdcIsEmpty();
    }

    /**
     * Bei fehlendem Header muss eine UUID erzeugt und in Response + MDC verfügbar sein.
     */
    @Test
    void shouldGenerateUuidWhenHeaderIsMissing() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, resp) -> assertValidUuid(MDC.get(TraceIdFilter.TRACE_ID_KEY));

        filter.doFilter(request, response, chain);

        assertValidUuid(response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertMdcIsEmpty();
    }

    /**
     * Leere oder reine Whitespace-Header sind fachlich äquivalent zu "nicht gesetzt".
     */
    @Test
    void shouldGenerateUuidWhenHeaderIsBlank() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "   ");

        filter.doFilter(request, response, (req, resp) -> assertValidUuid(MDC.get(TraceIdFilter.TRACE_ID_KEY)));

        assertValidUuid(response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertMdcIsEmpty();
    }

    /**
     * Der Filter darf vorhandene MDC-Werte pro Request überschreiben und danach wieder aufräumen.
     */
    @Test
    void shouldOverwriteOldMdcValueInsideRequestAndClearAfterwards() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MDC.put(TraceIdFilter.TRACE_ID_KEY, "stale-trace-id");

        filter.doFilter(request, response, (req, resp) -> {
            String traceIdInRequest = MDC.get(TraceIdFilter.TRACE_ID_KEY);
            assertNotNull(traceIdInRequest);
            assertFalse(traceIdInRequest.isBlank());
            assertFalse("stale-trace-id".equals(traceIdInRequest));
        });

        assertMdcIsEmpty();
    }

    /**
     * Auch im Fehlerfall muss der MDC-Eintrag aus dem finally-Block entfernt werden.
     */
    @Test
    void shouldClearMdcWhenFilterChainThrows() {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> filter.doFilter(request, response, (req, resp) -> {
                    throw new RuntimeException("boom");
                }));

        assertEquals("boom", thrown.getMessage());
        assertMdcIsEmpty();
    }

    /**
     * Konstante Namen sind Teil des Vertrages zu Logback-Pattern und HTTP-Headern.
     */
    @Test
    void shouldExposeStableContractConstants() {
        assertEquals("traceId", TraceIdFilter.TRACE_ID_KEY);
        assertEquals("X-Trace-Id", TraceIdFilter.TRACE_ID_HEADER);
    }

    private static void assertValidUuid(String maybeUuid) {
        assertNotNull(maybeUuid);
        assertFalse(maybeUuid.isBlank());
        UUID parsed = UUID.fromString(maybeUuid);
        assertNotNull(parsed);
    }

    private static void assertMdcIsEmpty() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null) {
            assertNull(context);
            return;
        }
        assertTrue(context.isEmpty());
    }
}
