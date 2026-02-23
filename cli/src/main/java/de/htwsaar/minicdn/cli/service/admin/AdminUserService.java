package de.htwsaar.minicdn.cli.service.admin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * Service-Klasse für die Verwaltung von Benutzern in der Admin-CLI. Bietet Funktionen zum Hinzufügen von Benutzern mit Namen und Rolle.
 */
public final class AdminUserService implements AutoCloseable {
    private final DSLContext dsl;
    private final Connection connection;

    public AdminUserService(DSLContext dsl) {
        this.dsl = dsl;
        this.connection = null;
    }

    public AdminUserService(String jdbcUrl) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl);
        this.dsl = DSL.using(this.connection, SQLDialect.SQLITE);
        initializeSchema();
    }

    /**
     * Rolle-String zu Integer-Mapping. "ADMIN" wird zu 1, "USER" zu 2. Andere Werte können direkt als Integer geparst werden.
     */
    private static final Map<String, Integer> ROLE_MAP = Map.of("ADMIN", 1, "USER", 2);

    /**
     * Initialisiert das Datenbankschema, falls es noch nicht existiert. Erstellt die Tabelle "users" mit den Spalten "id", "name" und "role".
     */
    private void initializeSchema() {
        dsl.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        role INTEGER NOT NULL
                    )
                """);
    }

    /**
     * Fügt einen neuen Benutzer mit dem angegebenen Namen und der Rolle hinzu. Gibt die ID des neuen Benutzers zurück oder -1 bei Fehlern.
     */
    public int addUser(String name, int role) {
        // Versuche, den Benutzer einzufügen und die Anzahl der betroffenen Zeilen zu erhalten
        int affected = dsl.insertInto(DSL.table(DSL.name("users")))
                .columns(DSL.field(DSL.name("name")), DSL.field(DSL.name("role")))
                .values(name, role)
                .execute();

        if (affected <= 0) {
            return -1;
        }

        // Hole die ID des zuletzt eingefügten Datensatzes. SQLite bietet die Funktion last_insert_rowid() dafür an.
        Record r = dsl.fetchOne("SELECT last_insert_rowid() AS id");
        if (r != null) {
            // Versuche zuerst, die ID als Integer abzurufen (typischerweise sollte sie das sein)
            Integer id = r.get("id", Integer.class);
            if (id != null) {
                return id;
            }
            // Falls das nicht funktioniert, versuche es als Number und konvertiere es dann zu int.
            // Das ist eine zusätzliche Absicherung, falls die Datenbank oder der JDBC-Treiber die ID als anderen numerischen Typ zurückgibt.
            Number n = r.get(0, Number.class);
            if (n != null) {
                return n.intValue();
            }
        }

        return -1;
    }

    /**
     * Implementierung der AutoCloseable-Schnittstelle, um die Datenbankverbindung ordnungsgemäß zu schließen, wenn der Service nicht mehr benötigt wird.
     */
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Listet Benutzer auf, optional gefiltert nach Rolle, und unterstützt Pagination mit Seitenzahl und Seitengröße. Gibt eine Liste von Maps zurück, die die Benutzerdaten enthalten.
     */
    public Object listUsers(String role, int page, int size) {
        int offset = (page - 1) * size;
        Condition condition = DSL.noCondition();

        Integer parsedRole = parseRole(role);
        if (parsedRole != null) {
            condition = DSL.field(DSL.name("role"), Integer.class).eq(parsedRole);
        }

        List<Map<String, Object>> rows = dsl.select(DSL.field(DSL.name("id")), DSL.field(DSL.name("name")), DSL.field(DSL.name("role")))
                .from(DSL.table(DSL.name("users")))
                .where(condition)
                .orderBy(DSL.field(DSL.name("id")))
                .limit(size)
                .offset(offset)
                .fetch()
                .intoMaps();

        return rows;
    }

    /**
     * Hilfsfunktion zum Parsen der Rolle aus einem String. Unterstützt benannte Rollen ("ADMIN", "USER") sowie direkte numerische IDs. Gibt null zurück, wenn die Rolle ungültig ist.
     */
    public static Integer parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        Integer mapped = ROLE_MAP.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        try {
            return Integer.parseInt(role.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Hilfsfunktion zum Erstellen einer Bedingung für die Abfrage basierend auf der Benutzer-ID. Wenn die ID null ist, wird eine Ausnahme ausgelöst, da die ID erforderlich ist.
     */
    private Condition buildUserCondition(Long userId) {
        if (userId != null) {
            return DSL.field(DSL.name("id"), Long.class).eq(userId);
        }

        throw new IllegalArgumentException("UserId must be provided");
    }

    /**
     * Findet einen Benutzer basierend auf der Benutzer-ID. Gibt eine Map mit den Benutzerdaten zurück oder null, wenn kein Benutzer gefunden wurde.
     */
    public Map<String, Object> findUser(Long userId) {
        Record record = dsl.select(DSL.field(DSL.name("id")), DSL.field(DSL.name("name")), DSL.field(DSL.name("role")))
                .from(DSL.table(DSL.name("users")))
                .where(buildUserCondition(userId))
                .fetchOne();
        return record == null ? null : record.intoMap();
    }

    /**
     * Entfernt einen Benutzer basierend auf der Benutzer-ID. Gibt true zurück, wenn ein Benutzer erfolgreich entfernt wurde, oder false, wenn kein Benutzer mit der angegebenen ID gefunden wurde.
     */
    public boolean removeUser(Long userId) {
        return dsl.deleteFrom(DSL.table(DSL.name("users")))
                .where(buildUserCondition(userId))
                .execute() > 0;
    }

}
