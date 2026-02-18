package de.htwsaar.minicdn.cli.util;

public final class JsonUtils {
    private JsonUtils() {}

    /**
     * Simple JSON string escaper. Not a full JSON serializer, just enough to safely embed JSON strings in CLI output.
     */
    public static String escapeJson(String json) {
        if (json == null) return "";
        return json.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
