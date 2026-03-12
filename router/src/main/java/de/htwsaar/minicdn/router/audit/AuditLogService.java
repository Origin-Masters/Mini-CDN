package de.htwsaar.minicdn.router.audit;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteDataSource;

/**
 * Persistiert und liest Audit-Logs für User-Aktionen.
 *
 * <p>Die Umsetzung ist bewusst schlank gehalten:
 * SQLite als append-only Tabelle, einfache Filter und CSV-Export.</p>
 */
@Service
public class AuditLogService {

    private final DSLContext dsl;
    private final Clock clock;

    /**
     * Erzeugt den Service über die konfigurierte JDBC-URL.
     *
     * @param jdbcUrl JDBC-URL (z. B. jdbc:sqlite:data/users.db)
     * @throws Exception falls die Datenquelle nicht initialisiert werden kann
     */
    @Autowired
    public AuditLogService(@Value("${app.audit.jdbc.url}") String jdbcUrl) throws Exception {
        this(jdbcUrl, Clock.systemUTC());
    }

    /**
     * Erzeugt den Service mit expliziter Uhr (für Tests).
     *
     * @param jdbcUrl JDBC-URL
     * @param clock Clock für deterministische Zeitstempel
     * @throws Exception falls die Datenquelle nicht initialisiert werden kann
     */
    public AuditLogService(String jdbcUrl, Clock clock) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(jdbcUrl);
        this.dsl = DSL.using(ds, SQLDialect.SQLITE);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        initSchema();
    }

    /**
     * Komfort-Factory für lokale/verteilte Skriptläufe.
     */
    public static AuditLogService fromProjectDb() throws Exception {
        Path dbFile = Path.of("data", "audit.db").toAbsolutePath();
        String jdbcUrl = "jdbc:sqlite:" + dbFile;
        return new AuditLogService(jdbcUrl, Clock.systemUTC());
    }

    /**
     * Schreibt einen Audit-Eintrag mit aktuellem Zeitstempel.
     */
    public void append(long userId, String action, String resource, int httpStatus) {
        if (userId <= 0) {
            return;
        }

        Instant now = clock.instant();
        AuditResult result = httpStatus >= 200 && httpStatus < 400 ? AuditResult.SUCCESS : AuditResult.FAILURE;

        dsl.execute(
                """
                INSERT INTO audit_logs (timestamp_utc, user_id, action, resource, result, http_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                now.toString(),
                userId,
                sanitize(action),
                sanitize(resource),
                result.name(),
                httpStatus);
    }

    /**
     * Liefert Audit-Einträge für einen spezifizierten User mit optionalen Filtern.
     */
    public List<AuditLogEntry> query(AuditQueryFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");

        List<Condition> conditions = new ArrayList<>();
        conditions.add(DSL.field("user_id").eq(filter.userId()));

        if (filter.from() != null) {
            conditions.add(DSL.field("timestamp_utc").ge(filter.from().toString()));
        }
        if (filter.to() != null) {
            conditions.add(DSL.field("timestamp_utc").le(filter.to().toString()));
        }
        if (filter.action() != null && !filter.action().isBlank()) {
            conditions.add(DSL.field("action").eq(filter.action().trim()));
        }
        if (filter.result() != null) {
            conditions.add(DSL.field("result").eq(filter.result().name()));
        }

        return dsl.select(
                        DSL.field("timestamp_utc"),
                        DSL.field("user_id"),
                        DSL.field("action"),
                        DSL.field("resource"),
                        DSL.field("result"),
                        DSL.field("http_status"))
                .from("audit_logs")
                .where(conditions)
                .orderBy(DSL.field("timestamp_utc").desc())
                .fetch(record -> new AuditLogEntry(
                        parseTimestamp(record.get("timestamp_utc", String.class)),
                        readLong(record.get("user_id", Number.class)),
                        record.get("action", String.class),
                        record.get("resource", String.class),
                        AuditResult.valueOf(record.get("result", String.class)),
                        readInt(record.get("http_status", Number.class))));
    }

    /**
     * Exportiert Audit-Einträge als CSV-String.
     */
    public String exportCsv(AuditQueryFilter filter) {
        List<AuditLogEntry> entries = query(filter);
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,userId,action,resource,result,httpStatus\n");
        for (AuditLogEntry entry : entries) {
            csv.append(escapeCsv(entry.timestamp().toString()))
                    .append(',')
                    .append(entry.userId())
                    .append(',')
                    .append(escapeCsv(entry.action()))
                    .append(',')
                    .append(escapeCsv(entry.resource()))
                    .append(',')
                    .append(entry.result().name())
                    .append(',')
                    .append(entry.httpStatus())
                    .append('\n');
        }
        return csv.toString();
    }

    /**
     * Initialisiert die Audit-Tabelle, falls sie nicht existiert.
     */
    private void initSchema() {
        dsl.execute(
                """
                CREATE TABLE IF NOT EXISTS audit_logs (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  timestamp_utc TEXT NOT NULL,
                  user_id INTEGER NOT NULL,
                  action TEXT NOT NULL,
                  resource TEXT NOT NULL,
                  result TEXT NOT NULL,
                  http_status INTEGER NOT NULL
                )
                """);

        dsl.execute("CREATE INDEX IF NOT EXISTS idx_audit_logs_user_time ON audit_logs(user_id, timestamp_utc)");
    }

    /**
     * Liest eine Number robust als long.
     */
    private static long readLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /**
     * Liest eine Number robust als int.
     */
    private static int readInt(Number value) {
        return value == null ? 0 : value.intValue();
    }
    /**
     * Parst einen ISO-Zeitstempel robust und fällt bei Fehlern auf die Epochenzeit zurück.
     */
    private static Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException ex) {
            return Instant.EPOCH;
        }
    }

    /**
     * Entfernt Null-Werte für stabile Persistenz.
     */
    private static String sanitize(String value) {
        return value == null ? "" : value;
    }

    /**
     * CSV-Escaping für Felder mit Trennzeichen, Quotes oder Zeilenumbrüchen.
     */
    private static String escapeCsv(String value) {
        String safe = sanitize(value);
        if (!safe.contains(",") && !safe.contains("\"") && !safe.contains("\n") && !safe.contains("\r")) {
            return safe;
        }

        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
