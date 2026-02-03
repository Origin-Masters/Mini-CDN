package de.htwsaar.minicdn.cli.service;

import de.htwsaar.minicdn.cli.db.tables.Users;
import de.htwsaar.minicdn.cli.db.tables.records.UsersRecord;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * Simple jOOQ-backed AdminUserService.
 */
public class AdminUserService implements AutoCloseable {
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
        UsersRecord rec = dsl.insertInto(Users.USERS)
                .columns(Users.USERS.NAME, Users.USERS.ROLE)
                .values(name, role)
                .returning(Users.USERS.ID)
                .fetchOne();

        if (rec != null && rec.getValue(Users.USERS.ID) != null) {
            return rec.getValue(Users.USERS.ID);
        }

        // fallback for sqlite if returning() is not supported
        Record1<Integer> r = (Record1<Integer>) dsl.fetchOne("SELECT last_insert_rowid()");
        return r != null && r.value1() != null ? r.value1() : -1;
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
