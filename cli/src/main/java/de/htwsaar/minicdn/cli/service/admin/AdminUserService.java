package de.htwsaar.minicdn.cli.service.admin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
