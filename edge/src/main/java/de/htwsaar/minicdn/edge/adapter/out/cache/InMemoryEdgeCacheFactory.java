package de.htwsaar.minicdn.edge.adapter.out.cache;

import de.htwsaar.minicdn.edge.domain.model.ReplacementStrategy;
import de.htwsaar.minicdn.edge.domain.port.EdgeCache;
import de.htwsaar.minicdn.edge.domain.port.EdgeCacheFactory;
import de.htwsaar.minicdn.edge.infrastructure.cache.LfuCacheStore;
import de.htwsaar.minicdn.edge.infrastructure.cache.LruCacheStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Adapter, der fachliche Cache-Strategien auf In-Memory-Implementierungen abbildet.
 */
@Component
@Profile("edge")
public class InMemoryEdgeCacheFactory implements EdgeCacheFactory {

    /**
     * Liefert die In-Memory-Implementierung für die gewünschte Strategie.
     *
     * @param strategy fachliche Cache-Strategie
     * @return passende Cache-Implementierung
     */
    @Override
    public EdgeCache create(ReplacementStrategy strategy) {
        return switch (strategy) {
            case LFU -> new LfuCacheStore();
            case LRU -> new LruCacheStore();
        };
    }
}
