package de.htwsaar.minicdn.cli.service.admin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-Memory Konfigurationsspeicher für die CLI (Stub).
 *
 * <p>Hinweis: Diese Implementierung ist pro JVM-Prozess global (static) und
 * dient aktuell nur zu Demo-/Stub-Zwecken.
 */
public final class AdminConfigService {

    private static final Map<String, String> CONFIG = new ConcurrentHashMap<>();

    private AdminConfigService() {
        // Utility class
    }

    /**
     * Setzt einen Konfigurationswert.
     *
     * @param key Konfigurationsschlüssel, darf nicht {@code null} oder blank sein
     * @param value Wert (darf {@code null} sein, wird dann als null gespeichert)
     * @return {@code true} wenn der Key valide war und gespeichert wurde, sonst {@code false}
     */
    public static boolean set(String key, String value) {
        if (key == null || key.isBlank()) {
            return false;
        }
        CONFIG.put(key, value);
        return true;
    }

    /**
     * Liest einen Konfigurationswert.
     *
     * @param key Konfigurationsschlüssel
     * @return Wert oder {@code null}, wenn nicht vorhanden/Key {@code null}
     */
    public static String get(String key) {
        if (key == null) {
            return null;
        }
        return CONFIG.get(key);
    }

    /**
     * Liefert eine unveränderliche Momentaufnahme aller Konfigurationen.
     *
     * @return immutable copy
     */
    public static Map<String, String> getAll() {
        return Map.copyOf(CONFIG);
    }

    /**
     * Formatiert die aktuelle Konfiguration für die Ausgabe (eine Zeile pro Eintrag).
     *
     * @return formatierte Zeilen, stabil sortiert nach Key
     */
    public static List<String> formatLines() {
        return getAll().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "%s=%s".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}
