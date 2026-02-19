package de.htwsaar.minicdn.common.logging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests für {@link LoggingConfig}.
 *
 * <p>Ziel: sicherstellen, dass die Konfiguration einen gültigen Filter bereitstellt,
 * der in den Service-Modulen importiert werden kann.</p>
 */
class LoggingConfigTest {

    @Test
    void shouldCreateTraceIdFilterBeanInstance() {
        LoggingConfig config = new LoggingConfig();

        TraceIdFilter filter = config.traceIdFilter();

        assertNotNull(filter);
        assertTrue(filter instanceof TraceIdFilter);
    }

    @Test
    void shouldReturnNewFilterOnDirectFactoryCalls() {
        LoggingConfig config = new LoggingConfig();

        TraceIdFilter first = config.traceIdFilter();
        TraceIdFilter second = config.traceIdFilter();

        assertNotSame(first, second);
    }
}
