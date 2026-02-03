package de.htwsaar.minicdn.cli.service;

import static org.junit.jupiter.api.Assertions.*;

import de.htwsaar.minicdn.cli.db.tables.Users;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

public class AdminUserServiceTest {

    @Test
    void addUser() throws Exception {
        Path tmpDir = Files.createTempDirectory("minicdn-test-");
        Path dbFile = tmpDir.resolve("minicdn.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile.toString();

        try {
            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl(jdbcUrl);

            try (Connection conn = ds.getConnection()) {
                DSLContext dsl = DSL.using(conn, SQLDialect.SQLITE);

                // create minimal users table
                dsl.execute(
                        "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, role INTEGER NOT NULL)");

                // use the service
                AdminUserService adminUserService = new AdminUserService(dsl);
                int id = adminUserService.addUser("Alice", 1);
                assertTrue(id > 0, "expected generated id > 0");

                // verify via jOOQ
                var rec = dsl.select(Users.USERS.ID, Users.USERS.NAME, Users.USERS.ROLE)
                        .from(Users.USERS)
                        .where(Users.USERS.ID.eq(id))
                        .fetchOne();

                assertNotNull(rec, "Inserted user not found");
                assertEquals("Alice", rec.get(Users.USERS.NAME));
                assertEquals(Integer.valueOf(1), rec.get(Users.USERS.ROLE));
            }
        } finally {
            // cleanup temp dir
            Files.walk(tmpDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(f -> f.delete());
        }
    }

    @Test
    void addUser_writesIntoProjectDb_usersDb() throws Exception {
        // This is an integration test: it touches the real project DB file.
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

            var rec = dsl.select(Users.USERS.ID, Users.USERS.NAME, Users.USERS.ROLE)
                    .from(Users.USERS)
                    .where(Users.USERS.ID.eq(id))
                    .fetchOne();

            assertNotNull(rec, "Inserted user not found in project DB");
            assertEquals(testName, rec.get(Users.USERS.NAME));
            assertEquals(Integer.valueOf(1), rec.get(Users.USERS.ROLE));
        } finally {
            // Cleanup: remove the test row so the project DB is not polluted
            if (insertedId != null) {
                SQLiteDataSource cleanupDs = new SQLiteDataSource();
                cleanupDs.setUrl(jdbcUrl);
                try (Connection cleanupConn = cleanupDs.getConnection()) {
                    DSLContext cleanupDsl = DSL.using(cleanupConn, SQLDialect.SQLITE);
                    cleanupDsl
                            .deleteFrom(Users.USERS)
                            .where(Users.USERS.ID.eq(insertedId))
                            .execute();
                }
            }
        }
    }
}
