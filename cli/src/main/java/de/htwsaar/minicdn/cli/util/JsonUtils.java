package de.htwsaar.minicdn.cli.util;

public final class JsonUtils {
    private JsonUtils() {
    }

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
}


