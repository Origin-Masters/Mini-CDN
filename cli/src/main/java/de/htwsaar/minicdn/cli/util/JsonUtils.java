package de.htwsaar.minicdn.cli.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public final class JsonUtils {
    private JsonUtils() {}

    /**
     * Hilfsfunktion zum Escapen von JSON-Strings, damit sie sicher in CLI-Ausgaben oder als Werte in JSON-Strukturen eingebettet werden können.
     * Ersetzt Backslashes, Anführungszeichen und Zeilenumbrüche durch ihre escaped-Versionen.
     */
    public static String escapeJson(String json) {
        if (json == null) return "";
        return json.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Hilfsfunktion zum Formatieren von JSON-Strings mit Einrückungen und Zeilenumbrüchen, um die Lesbarkeit in CLI-Ausgaben zu verbessern.
     */
    public static String formatJson(String json) {
        if (json == null) return "";
        String trimmed = json.trim();
        if (trimmed.isEmpty() || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
            return json;
        }

        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                sb.append(c);
                continue;
            }

            if (!inQuotes) {
                switch (c) {
                    case '{':
                    case '[':
                        sb.append(c).append('\n');
                        indent++;
                        appendIndent(sb, indent);
                        continue;
                    case '}':
                    case ']':
                        sb.append('\n');
                        indent = Math.max(0, indent - 1);
                        appendIndent(sb, indent);
                        sb.append(c);
                        continue;
                    case ',':
                        sb.append(c).append('\n');
                        appendIndent(sb, indent);
                        continue;
                    case ':':
                        sb.append(": ");
                        continue;
                    default:
                        if (Character.isWhitespace(c)) continue;
                }
            }

            sb.append(c);
        }

        return sb.toString();
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(" ");
        }
    }

    /**
     * Extrahiert ein JSON-Objekt als sortierte Long-Map.
     *
     * @param node JSON-Objekt
     * @return sortierte Map mit numerischen Werten
     */
    public static Map<String, Long> toSortedLongMap(JsonNode node) {
        Map<String, Long> values = new TreeMap<>();
        if (!node.isObject()) {
            return values;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            values.put(entry.getKey(), Math.max(0L, entry.getValue().asLong(0L)));
        }
        return values;
    }

    /**
     * Hilfsfunktion zum URL-Encoden von Strings, um sie sicher in URLs oder als Werte in JSON-Strukturen einzubetten.
     * Verwendet UTF-8 als Standard-Encoding und behandelt null-Werte als leere Strings.
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
