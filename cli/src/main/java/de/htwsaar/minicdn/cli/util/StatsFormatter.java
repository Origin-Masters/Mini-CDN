package de.htwsaar.minicdn.cli.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Hilfsklasse zum Formatieren von Statistiken aus der Admin-API für die Konsolenausgabe.
 */
public final class StatsFormatter {

    private StatsFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gibt die Gesamtzahl an Downloads je Datei formatiert aus.
     *
     * @param out         Ziel-Output
     * @param byFileTotal JSON-Objekt Datei -> Download-Count
     */
    public static void printDownloadTotals(PrintWriter out, JsonNode byFileTotal) {
        out.println("  downloadsByFileTotal:");
        if (!byFileTotal.isObject() || byFileTotal.isEmpty()) {
            out.println("    (none)");
            return;
        }

        JsonUtils.toSortedLongMap(byFileTotal).forEach((path, count) -> out.printf("    %s : %d%n", path, count));
    }

    /**
     * Gibt Downloadzahlen je Datei und Edge formatiert aus.
     *
     * @param out          Ziel-Output
     * @param byFileByEdge JSON-Objekt Datei -> (Edge-URL -> Download-Count)
     */
    public static void printDownloadByEdge(PrintWriter out, JsonNode byFileByEdge) {
        out.println("  downloadsByFileByEdge:");
        if (!byFileByEdge.isObject() || byFileByEdge.isEmpty()) {
            out.println("    (none)");
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fileIterator = byFileByEdge.fields();
        Map<String, JsonNode> sortedByFile = new TreeMap<>();
        while (fileIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = fileIterator.next();
            sortedByFile.put(entry.getKey(), entry.getValue());
        }

        sortedByFile.forEach((path, node) -> {
            out.printf("    %s%n", path);
            JsonUtils.toSortedLongMap(node).forEach((edgeUrl, count) -> out.printf("      %s : %d%n", edgeUrl, count));
        });
    }
}
