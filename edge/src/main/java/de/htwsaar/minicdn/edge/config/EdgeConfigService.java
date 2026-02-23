package de.htwsaar.minicdn.edge.config;

import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zentraler, thread-sicherer Runtime-Config-Store.
 *
 * <p>Domain- und Cache-Schicht lesen Konfiguration nur über diesen Service –
 * so sind Live-Updates ohne Neustart möglich, ohne irgendeine andere Klasse anzupassen.</p>
 */
public class EdgeConfigService {

    private final AtomicReference<EdgeRuntimeConfig> ref;

    /**
     * Erstellt den Service mit der initialen Konfiguration aus den Properties.
     *
     * @param initial Startkonfiguration (darf nicht {@code null} sein)
     */
    public EdgeConfigService(EdgeRuntimeConfig initial) {
        this.ref = new AtomicReference<>(Objects.requireNonNull(initial, "initial config must not be null"));
    }

    /**
     * Gibt die aktuell aktive Konfiguration zurück.
     *
     * @return aktuelle {@link EdgeRuntimeConfig}
     */
    public EdgeRuntimeConfig current() {
        return ref.get();
    }

    /**
     * Überschreibt die Konfiguration vollständig (atomisch).
     *
     * @param next neue Konfiguration (darf nicht {@code null} sein)
     * @return die neue Konfiguration
     */
    public EdgeRuntimeConfig update(EdgeRuntimeConfig next) {
        return ref.getAndSet(Objects.requireNonNull(next, "next config must not be null"));
    }

    /**
     * Partielles Update: nur nicht-{@code null}-Felder werden übernommen.
     *
     * @param region              neue Region oder {@code null} (beibehaltung)
     * @param defaultTtlMs        neue Standard-TTL oder {@code null}
     * @param maxEntries          neues Eintrags-Limit oder {@code null}
     * @param replacementStrategy neue Strategie oder {@code null}
     * @return aktualisierte Konfiguration
     */
    public EdgeRuntimeConfig patch(
            String region, Long defaultTtlMs, Integer maxEntries, ReplacementStrategy replacementStrategy) {

        return ref.updateAndGet(cur -> new EdgeRuntimeConfig(
                region != null ? region.trim() : cur.region(),
                defaultTtlMs != null ? Math.max(0, defaultTtlMs) : cur.defaultTtlMs(),
                maxEntries != null ? Math.max(0, maxEntries) : cur.maxEntries(),
                replacementStrategy != null ? replacementStrategy : cur.replacementStrategy()));
    }
}
