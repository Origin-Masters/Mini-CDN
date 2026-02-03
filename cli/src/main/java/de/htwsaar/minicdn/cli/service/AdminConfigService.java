package de.htwsaar.minicdn.cli.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdminConfigService {
    private static final Map<String, String> config = new ConcurrentHashMap<>();

    public static boolean set(String key, String value) {
        if (key == null || key.isBlank()) {
            return false;
        }
        config.put(key, value);
        return true;
    }

    public static String get(String key) {
        if (key == null) return null;
        return config.get(key);
    }

    public static Map<String, String> getAll() {
        return Map.copyOf(config);
    }

    // Print all configuration entries to stdout in "key=value" lines.
    public static void show() {
        Map<String, String> all = getAll();
        if (all.isEmpty()) {
            System.out.println("[ADMIN] No global configuration set");
            return;
        }
        System.out.println("[ADMIN] Global configuration:");
        all.forEach((k, v) -> System.out.printf("[ADMIN] %s=%s%n", k, v));
    }
}
