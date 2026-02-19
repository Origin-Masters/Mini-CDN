package de.htwsaar.minicdn.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Tests für die Edge-Metriken (Hits/Misses/Requests pro Zeitfenster). */
class EdgeMetricsServiceTest {

    @Test
    void shouldTrackHitsMissesAndExactRequestsPerWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        EdgeMetricsService service = new EdgeMetricsService(clock);

        service.recordMiss();
        clock.plusSeconds(5);
        service.recordHit();
        clock.plusSeconds(5);
        service.recordHit();

        EdgeMetricsService.EdgeStatsSnapshot first = service.snapshot(60, 7);
        assertEquals(3, first.totalRequests());
        assertEquals(3, first.requestsPerWindow());
        assertEquals(2, first.cacheHits());
        assertEquals(1, first.cacheMisses());
        assertEquals(7, first.filesCached());

        clock.plusSeconds(61);
        EdgeMetricsService.EdgeStatsSnapshot second = service.snapshot(60, 2);
        assertEquals(3, second.totalRequests());
        assertEquals(0, second.requestsPerWindow());
        assertEquals(2, second.cacheHits());
        assertEquals(1, second.cacheMisses());
        assertEquals(2, second.filesCached());
    }

    /** Einfache verstellbare Uhr für deterministische Zeitfenster-Tests. */
    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant start) {
            this.current = start;
        }

        void plusSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
