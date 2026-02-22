package de.htwsaar.minicdn.edge;

import de.htwsaar.minicdn.edge.adapter.http.HttpOriginClient;
import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;
import de.htwsaar.minicdn.edge.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.config.EdgeRuntimeConfig;
import de.htwsaar.minicdn.edge.domain.OriginClient;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * Zentrale Spring-Verdrahtung der Edge-Komponenten.
 *
 * <p>Schichtung: Controller → Service → Domain/Ports → Adapter/Infrastructure</p>
 */
@Configuration
@Profile("edge")
public class EdgeBeans {

    /**
     * Systemuhr für den gesamten Edge-Kontext.
     *
     * @return UTC-Clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * HTTP-Client-Template für Origin-Zugriffe.
     *
     * @return Standard-RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Initialisiert die live-änderbare Edge-Konfiguration aus den Properties.
     *
     * @param region              konfigurierte Region (Standard: "unknown")
     * @param defaultTtlMs        Standard-TTL in ms (Standard: 60000)
     * @param maxEntries          maximale Cache-Einträge (Standard: 100)
     * @param evictionStrategy    Replacement-Strategie als String (Standard: "LRU")
     * @return initialisierter {@link EdgeConfigService}
     */
    @Bean
    public EdgeConfigService edgeConfigService(
            @Value("${edge.region:unknown}") String region,
            @Value("${edge.cache.ttl-ms:60000}") long defaultTtlMs,
            @Value("${edge.cache.max-entries:100}") int maxEntries,
            @Value("${edge.cache.eviction-strategy:LRU}") String evictionStrategy) {

        EdgeRuntimeConfig initial = new EdgeRuntimeConfig(
                region,
                Math.max(0, defaultTtlMs),
                Math.max(0, maxEntries),
                ReplacementStrategy.valueOf(evictionStrategy.trim().toUpperCase()));
        return new EdgeConfigService(initial);
    }

    /**
     * Adapter-Implementierung des {@link OriginClient}-Ports via HTTP.
     *
     * @param rt             RestTemplate
     * @param originBaseUrl  Basis-URL des Origin-Servers
     * @return {@link HttpOriginClient}
     */
    @Bean
    public OriginClient originClient(RestTemplate rt, @Value("${origin.base-url}") String originBaseUrl) {
        return new HttpOriginClient(rt, URI.create(originBaseUrl));
    }
}
