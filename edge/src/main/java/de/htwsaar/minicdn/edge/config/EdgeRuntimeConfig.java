package de.htwsaar.minicdn.edge.config;

import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;

/**
 * Laufzeit-Konfiguration der Edge-Node – live änderbar ohne Neustart.
 *
 * @param region              Region dieser Edge-Node (z. B. "eu-west")
 * @param defaultTtlMs        Standard-TTL für gecachte Objekte in ms
 * @param maxEntries          maximale Cache-Einträge (0 = unbegrenzt)
 * @param replacementStrategy LRU oder LFU
 */
public record EdgeRuntimeConfig(
        String region, long defaultTtlMs, int maxEntries, ReplacementStrategy replacementStrategy) {}
