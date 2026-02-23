package de.htwsaar.minicdn.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.cli.service.admin.AdminUserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

/**
 * Tests für {@link AdminUserService} ohne harte Abhängigkeit auf jOOQ-Codegen-Klassen.
 */
public class AdminUserServiceTest {

    /**
     * Verifiziert, dass ein Benutzer in einer temporären SQLite-DB angelegt wird.
     *
     * @throws Exception bei Setup-/DB-Fehlern
     */
    @Test
    void addUser() throws Exception {
        Path tmpDir = Files.createTempDirectory("minicdn-test-");
        Path dbFile = tmpDir.resolve("minicdn.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        try {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);

            try (Connection conn = ds.getConnection()) {
                DSLContext dsl = DSL.using(conn, SQLDialect.SQLITE);

                dsl.execute(
                        "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, role INTEGER NOT NULL)");

                AdminUserService adminUserService = new AdminUserService(dsl);
                int id = adminUserService.addUser("Alice", 1);
                assertTrue(id > 0, "expected generated id > 0");

                Record3<Integer, String, Integer> rec = dsl
                        .select(
                                DSL.field(DSL.name("id"), Integer.class),
                                DSL.field(DSL.name("name"), String.class),
                                DSL.field(DSL.name("role"), Integer.class))
                        .from(DSL.table(DSL.name("users")))
                        .where(DSL.field(DSL.name("id"), Integer.class).eq(id))
                        .fetchOne();

                assertNotNull(rec, "Inserted user not found");
                assertEquals("Alice", rec.get(DSL.field(DSL.name("name"), String.class)));
                assertEquals(Integer.valueOf(1), rec.get(DSL.field(DSL.name("role"), Integer.class)));
            }
        } finally {
            Files.walk(tmpDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> file.delete());
        }
    }

    /**
     * Integrationstest gegen die Projekt-DB {@code data/users.db}.
     *
     * @throws Exception bei Setup-/DB-Fehlern
     */
    @Test
    void addUser_writesIntoProjectDb_usersDb() throws Exception {
        Path projectDb = Path.of("data", "users.db");
        assertTrue(Files.exists(projectDb), "Expected project DB to exist at: " + projectDb.toAbsolutePath());

        String jdbcUrl = "jdbc:sqlite:" + projectDb.toAbsolutePath();
        Integer insertedId = null;

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(jdbcUrl);

        try (Connection conn = ds.getConnection()) {
            DSLContext dsl = DSL.using(conn, SQLDialect.SQLITE);

            AdminUserService adminUserService = new AdminUserService(dsl);

            String testName = "IT-AdminUserServiceTest-" + System.currentTimeMillis();
            int id = adminUserService.addUser(testName, 1);
            insertedId = id;

            assertTrue(id > 0, "expected generated id > 0");

            Record3<Integer, String, Integer> rec = dsl
                    .select(
                            DSL.field(DSL.name("id"), Integer.class),
                            DSL.field(DSL.name("name"), String.class),
                            DSL.field(DSL.name("role"), Integer.class))
                    .from(DSL.table(DSL.name("users")))
                    .where(DSL.field(DSL.name("id"), Integer.class).eq(id))
                    .fetchOne();

            assertNotNull(rec, "Inserted user not found in project DB");
            assertEquals(testName, rec.get(DSL.field(DSL.name("name"), String.class)));
            assertEquals(Integer.valueOf(1), rec.get(DSL.field(DSL.name("role"), Integer.class)));
        } finally {
            if (insertedId != null) {
                SQLiteDataSource cleanupDs = new SQLiteDataSource();
                cleanupDs.setUrl(jdbcUrl);
                try (Connection cleanupConn = cleanupDs.getConnection()) {
                    DSLContext cleanupDsl = DSL.using(cleanupConn, SQLDialect.SQLITE);
                    cleanupDsl
                            .deleteFrom(DSL.table(DSL.name("users")))
                            .where(DSL.field(DSL.name("id"), Integer.class).eq(insertedId))
                            .execute();
                }
            }
        }
    }
}
