package de.htwsaar.minicdn.router.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests für die Kernlogik der Audit-Persistenz, Filterung und CSV-Exportfunktion.
 */
class AuditLogServiceTest {

    @Test
    void shouldPersistAndQueryByUserWithResultFilter() throws Exception {
        Path db = Files.createTempFile("audit-test-", ".db");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuditLogService service = new AuditLogService("jdbc:sqlite:" + db.toAbsolutePath(), clock);

        service.append(7L, "GET /api/cdn/admin/users", "/api/cdn/admin/users", 200);
        clock.plusSeconds(10);
        service.append(7L, "DELETE /api/cdn/admin/users/3", "/api/cdn/admin/users/3", 404);
        clock.plusSeconds(10);
        service.append(8L, "GET /api/cdn/admin/users", "/api/cdn/admin/users", 200);

        List<AuditLogEntry> onlyFailures =
                service.query(new AuditQueryFilter(7L, null, null, null, AuditResult.FAILURE));

        assertEquals(1, onlyFailures.size());
        assertEquals(7L, onlyFailures.get(0).userId());
        assertEquals(AuditResult.FAILURE, onlyFailures.get(0).result());
        assertEquals(404, onlyFailures.get(0).httpStatus());
    }

    @Test
    void shouldExportCsvWithHeaderAndRows() throws Exception {
        Path db = Files.createTempFile("audit-export-test-", ".db");
        AuditLogService service = new AuditLogService("jdbc:sqlite:" + db.toAbsolutePath(), Clock.systemUTC());

        service.append(3L, "POST /api/cdn/auth/login", "/api/cdn/auth/login", 200);

        String csv = service.exportCsv(new AuditQueryFilter(3L, null, null, null, null));

        assertTrue(csv.startsWith("timestamp,userId,action,resource,result,httpStatus\n"));
        assertTrue(csv.contains(",3,POST /api/cdn/auth/login,/api/cdn/auth/login,SUCCESS,200"));
    }

    /**
     * Kleine verstellbare Clock für deterministische Tests.
     */
    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant start) {
            this.current = start;
        }

        private void plusSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
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
            return this.current;
        }
    }
}
