package de.htwsaar.minicdn.cli.util;

public class DatabaseUtils {
    private DatabaseUtils() {}

    /**
     * Hilfsfunktion zum Auflösen der JDBC-URL für die Datenbankverbindung. Prüft zuerst die Umgebungsvariable "MINICDN_JDBC_URL",
     * dann "MINICDNJDBCURL" (für ältere Versionen), und fällt schließlich auf eine Standard-SQLite-URL zurück, wenn keine Umgebungsvariable gesetzt ist.
     *
     * @return Die aufgelöste JDBC-URL als String.
     */
    public static String resolveJdbcUrl() {
        String jdbcUrl = System.getenv("MINICDN_JDBC_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = System.getenv("MINICDNJDBCURL");
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = "jdbc:sqlite:cli/data/users.db";
        }
        return jdbcUrl;
    }
}
