package de.htwsaar.minicdn.cli.util;

public class DatabaseUtils {
    private DatabaseUtils() {}

    public static String resolveJdbcUrl() {
        String jdbcUrl = System.getenv("MINICDN_JDBC_URL");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = System.getenv("MINICDNJDBCURL");
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = "jdbc:sqlite:./minicdn.db";
        }
        return jdbcUrl;
    }
}
