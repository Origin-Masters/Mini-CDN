package de.htwsaar.minicdn.cli.adapter.in.cli.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Kleine Hilfsfunktionen für JSON-nahe CLI-Aufgaben.
 *
 * <p>Die Klasse deckt bewusst nur einfache Fälle ab, etwa Escaping,
 * kompakte Pretty-Print-Ausgabe und das Extrahieren sortierter Zahlenwerte
 * aus Jackson-Knoten.</p>
 */
public final class JsonUtils {
    private JsonUtils() {}

    /**
     * Escaped einen Text für die sichere Einbettung als JSON-Stringwert.
     *
     * @param json roher Text
     * @return JSON-sicherer String ohne umgebende Anführungszeichen
     */
    public static String escapeJson(String json) {
        if (json == null) return "";
        return json.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Formatiert einen JSON-String leicht lesbarer für die Konsolenausgabe.
     *
     * <p>Ist der Eingabetext kein JSON-Objekt und kein JSON-Array, wird er
     * unverändert zurückgegeben.</p>
     *
     * @param json roher JSON-Text
     * @return lesbarer formatierter Text
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
     * URL-encodiert einen Wert mit UTF-8.
     *
     * @param value roher URL-Bestandteil
     * @return encodierter Wert oder leerer String bei {@code null}
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
