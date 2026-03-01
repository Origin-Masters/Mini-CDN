package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.dto.UserResult;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service-Klasse für die Verwaltung von Usern in der Router-Anwendung.
 * Verwaltet die Verbindung zur SQLite-Datenbank und bietet Methoden zum Hinzufügen, Auflisten und Löschen von Usern.
 */
@Service
public class RouterUserService implements AutoCloseable {

    private final Connection connection;

    public RouterUserService(@Value("${app.jdbc.url}") String jdbcUrl) throws Exception {
        this.connection = DriverManager.getConnection(jdbcUrl);
        initSchema();
    }

    public static RouterUserService fromProjectDb() throws Exception {
        Path dbFile = Path.of("data", "users.db").toAbsolutePath();
        String jdbcUrl = "jdbc:sqlite:" + dbFile;
        return new RouterUserService(jdbcUrl);
    }

    /**
     * Initialisiert die Datenbank, indem die notwendige Tabelle für die User angelegt wird, falls sie noch nicht existiert.
     * Diese Methode wird im Konstruktor aufgerufen, um sicherzustellen, dass die Datenbank bereit ist, bevor andere Methoden aufgerufen werden.
     */
    private void initSchema() throws Exception {
        try (var stmt = connection.createStatement()) {
            stmt.execute(
                    """
                            CREATE TABLE IF NOT EXISTS users (
                                id   INTEGER PRIMARY KEY AUTOINCREMENT,
                                name TEXT NOT NULL,
                                role INTEGER NOT NULL
                            )
                            """);
        }
    }

    /**
     * Fügt einen neuen User mit dem angegebenen Namen und der Rolle hinzu. Gibt die generierte ID des neuen Users zurück.
     * Validiert die Eingaben, um sicherzustellen, dass der Name nicht leer ist und die Rolle einen gültigen Wert hat (0=USER, 1=ADMIN).
     * Wirft eine IllegalArgumentException bei ungültigen Eingaben und eine IllegalStateException bei unerwarteten Fehlern während der Datenbankoperationen.
     */
    public long addUser(String name, int role) throws Exception {
        if (role < 0 || role > 2) {
            throw new IllegalArgumentException("Invalid role: " + role + ". Valid values: 0=USER, 1=ADMIN");
        }
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        try (var ps = connection.prepareStatement(
                "INSERT INTO users(name, role) VALUES(?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, role);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("insert failed");
            }
            try (var rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            throw new IllegalStateException("no generated id");
        }
    }

    /**
     * Gibt eine Liste aller User zurück, sortiert nach ID. Jede User-Information wird als UserResult-Objekt zurückgegeben.
     * Wirft eine IllegalStateException bei unerwarteten Fehlern während der Datenbankoperationen.
     */
    public List<UserResult> listUsers() throws Exception {
        List<UserResult> result = new ArrayList<>();
        try (var ps = connection.prepareStatement("SELECT id, name, role FROM users ORDER BY id")) {
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new UserResult(rs.getLong("id"), rs.getString("name"), rs.getInt("role")));
                }
            }
        }
        return result;
    }

    /**
     * Löscht den User mit der angegebenen ID. Gibt true zurück, wenn ein User gelöscht wurde, oder false, wenn kein User mit der ID existierte.
     * Wirft eine IllegalStateException bei unerwarteten Fehlern während der Datenbankoperationen.
     */
    public boolean deleteUser(long id) throws Exception {
        try (var ps = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Schließt die Datenbankverbindung. Diese Methode wird automatisch aufgerufen, wenn die RouterUserService-Instanz geschlossen wird (z.B. am Ende der Anwendungslaufzeit).
     * Wirft eine Exception, wenn ein Fehler beim Schließen der Verbindung auftritt.
     */
    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
