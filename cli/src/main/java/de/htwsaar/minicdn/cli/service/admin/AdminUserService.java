package de.htwsaar.minicdn.cli.service.admin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * Simple jOOQ-backed AdminUserService.
 */
public final class AdminUserService implements AutoCloseable {
    private final DSLContext dsl;
    private final Connection connection; // only set when constructed from JDBC URL

    public AdminUserService(DSLContext dsl) {
        this.dsl = dsl;
        this.connection = null;
    }

    public AdminUserService(String jdbcUrl) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl);
        this.dsl = DSL.using(this.connection, SQLDialect.SQLITE);
    }

    /**
     * Insert a user and return the generated id (>0) or -1 on failure.
     */
    public int addUser(String name, int role) {
        // Use generic table/field references instead of generated jOOQ classes
        int affected = dsl.insertInto(DSL.table(DSL.name("users")))
                .columns(DSL.field(DSL.name("name")), DSL.field(DSL.name("role")))
                .values(name, role)
                .execute();

        if (affected <= 0) {
            return -1;
        }

        // fallback for sqlite to obtain last inserted id
        Record r = dsl.fetchOne("SELECT last_insert_rowid() AS id");
        if (r != null) {
            // try named retrieval first
            Integer id = r.get("id", Integer.class);
            if (id != null) {
                return id;
            }
            // fall back to numeric retrieval and convert
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
