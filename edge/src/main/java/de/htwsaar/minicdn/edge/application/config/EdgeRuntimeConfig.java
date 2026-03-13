package de.htwsaar.minicdn.edge.application.config;

import de.htwsaar.minicdn.edge.infrastructure.cache.ReplacementStrategy;

/**
 * Laufzeit-Konfiguration der Edge-Node – live änderbar ohne Neustart.
 *
 * @param region              Region dieser Edge-Node (z. B. "eu-west")
 * @param defaultTtlMs        Standard-TTL für gecachte Objekte in ms
 * @param maxEntries          maximale Cache-Einträge (0 = unbegrenzt)
 * @param replacementStrategy LRU oder LFU
 * @param originBaseUrl       aktuell verwendete Origin-Basis-URL
 */
public record EdgeRuntimeConfig(
        String region,
        long defaultTtlMs,
        int maxEntries,
        ReplacementStrategy replacementStrategy,
        String originBaseUrl) {}
