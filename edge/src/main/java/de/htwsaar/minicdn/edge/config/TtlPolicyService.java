package de.htwsaar.minicdn.edge.config;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * TTL-Policies für einzelne Pfade oder Prefixe.
 *
 * <p>Regel: längster passender Prefix gewinnt;
 * Fallback ist {@code defaultTtlMs} aus {@link EdgeConfigService}.</p>
 */
@Service
public class TtlPolicyService {

    private final Map<String, Long> ttlByPrefixMs = new ConcurrentHashMap<>();

    /**
     * Setzt eine TTL-Policy für einen Pfad-Prefix.
     *
     * @param prefix Pfad-Prefix
     * @param ttlMs  TTL in ms
     */
    public void setPrefixTtlMs(String prefix, long ttlMs) {
        if (prefix == null || prefix.isBlank()) return;
        ttlByPrefixMs.put(prefix.trim(), Math.max(0, ttlMs));
    }

    /**
     * Entfernt eine TTL-Policy für einen Pfad-Prefix.
     *
     * @param prefix zu entfernender Prefix
     * @return {@code true} wenn eine Policy entfernt wurde
     */
    public boolean removePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return false;
        return ttlByPrefixMs.remove(prefix.trim()) != null;
    }

    /**
     * Gibt eine unveränderliche Kopie aller TTL-Policies zurück.
     *
     * @return Map von Prefix → TTL in ms
     */
    public Map<String, Long> snapshot() {
        return Map.copyOf(ttlByPrefixMs);
    }

    /**
     * Bestimmt die effektive TTL für einen Pfad.
     * Längster passender Prefix gewinnt; Fallback ist {@code defaultTtlMs}.
     *
     * @param path         zu prüfender Pfad
     * @param defaultTtlMs Standard-TTL
     * @return effektive TTL in ms
     */
    public long resolveTtlMs(String path, long defaultTtlMs) {
        if (path == null || path.isBlank()) return defaultTtlMs;
        return ttlByPrefixMs.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(defaultTtlMs);
    }
}
