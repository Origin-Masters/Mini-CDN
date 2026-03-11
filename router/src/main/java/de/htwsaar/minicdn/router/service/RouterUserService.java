package de.htwsaar.minicdn.router.service;

import static de.htwsaar.minicdn.router.db.Tables.USERS;

import de.htwsaar.minicdn.router.dto.UserResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteDataSource;

/**
 * Service-Klasse für die Verwaltung von Usern in der Router-Anwendung.
 * Verwendet jOOQ für typsichere Datenbankzugriffe auf SQLite.
 */
@Service
public class RouterUserService implements AutoCloseable {

    private final DSLContext dsl;

    public RouterUserService(@Value("${app.jdbc.url}") String jdbcUrl) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(jdbcUrl);
        this.dsl = DSL.using(ds, SQLDialect.SQLITE);
        initSchema();
    }

    public static RouterUserService fromProjectDb() throws Exception {
        Path dbFile = Path.of("data", "users.db").toAbsolutePath();
        String jdbcUrl = "jdbc:sqlite:" + dbFile;
        return new RouterUserService(jdbcUrl);
    }

    /**
     * Initialisiert das Datenbankschema, falls noch nicht vorhanden.
     */
    private void initSchema() {
        dsl.execute(
                """
            CREATE TABLE IF NOT EXISTS users (
              id   INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT    NOT NULL,
              role INTEGER NOT NULL
            )
            """);
    }

    /**
     * Fügt einen neuen User hinzu und gibt die generierte ID zurück.
     */
    public long addUser(String name, int role) {
        if (role < 0 || role >= 2)
            throw new IllegalArgumentException("Invalid role " + role + ". Valid values: 0=USER, 1=ADMIN");
        if (name == null || name.trim().isBlank()) throw new IllegalArgumentException("Name cannot be empty");

        return dsl.insertInto(USERS, USERS.NAME, USERS.ROLE)
                .values(name.trim(), role)
                .returningResult(USERS.ID)
                .fetchOne()
                .value1()
                .longValue();
    }

    /**
     * Gibt alle User sortiert nach ID zurück.
     */
    public List<UserResult> listUsers() {
        return dsl.selectFrom(USERS).orderBy(USERS.ID).fetch(r -> new UserResult(r.getId(), r.getName(), r.getRole()));
    }

    /**
     * Sucht einen User anhand der ID.
     *
     * @param id technische User-ID
     * @return gefundener User oder leer, falls kein Treffer existiert
     */
    public Optional<UserResult> findUserById(long id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq((int) id))
                .fetchOptional(r -> new UserResult(r.getId(), r.getName(), r.getRole()));
    }

    /**
     * Sucht einen User anhand des eindeutigen Namens.
     *
     * @param name Username des Users
     * @return gefundener User oder leer, falls kein Treffer existiert
     */
    public Optional<UserResult> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return dsl.selectFrom(USERS)
                .where(USERS.NAME.eq(name.trim()))
                .fetchOptional(r -> new UserResult(r.getId(), r.getName(), r.getRole()));
    }

    /**
     * Löscht einen User anhand der ID. Gibt true zurück, wenn ein User gelöscht wurde.
     */
    public boolean deleteUser(long id) {
        return dsl.deleteFrom(USERS).where(USERS.ID.eq((int) id)).execute() > 0;
    }

    @Override
    public void close() {
        // DSLContext über DataSource – kein explizites Schließen nötig
    }
}
