package de.htwsaar.minicdn.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Tests für die zeitfensterbasierte Router-Metrikberechnung. */
class RouterStatsServiceTest {

    @Test
    void shouldCalculateExactWindowCountsAndActiveClients() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RouterStatsService service = new RouterStatsService(clock);

        service.recordRequest("EU", "alice");
        clock.plusSeconds(10);
        service.recordRequest("EU", "bob");
        clock.plusSeconds(10);
        service.recordRequest("US", "alice");

        RouterStatsService.RouterStatsSnapshot first = service.snapshot(60);
        assertEquals(3, first.totalRequests());
        assertEquals(3, first.requestsPerWindow());
        assertEquals(2, first.activeClients());

        clock.plusSeconds(61);
        RouterStatsService.RouterStatsSnapshot second = service.snapshot(60);
        assertEquals(3, second.totalRequests());
        assertEquals(0, second.requestsPerWindow());
        assertEquals(0, second.activeClients());
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
