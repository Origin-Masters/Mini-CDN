package de.htwsaar.minicdn.edge.bootstrap;

import de.htwsaar.minicdn.edge.application.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.application.config.TtlPolicyService;
import de.htwsaar.minicdn.edge.application.file.EdgeFileService;
import de.htwsaar.minicdn.edge.infrastructure.persistence.EdgeRuntimeStateStore;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stellt beim Start persistierten Laufzeitstatus der Edge-Node wieder her.
 *
 * <p>Geladen werden Konfiguration, TTL-Policies und der gespeicherte Cache-Zustand,
 * damit die Instanz nach einem Neustart mit dem zuletzt bekannten Zustand weiterläuft.</p>
 */
@Component
@Profile("edge")
public class EdgeRecoveryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EdgeRecoveryBootstrap.class);

    /** Persistierter Laufzeitstatus der Edge-Node. */
    private final EdgeRuntimeStateStore runtimeStateStore;

    /** Nimmt wiederhergestellte Konfigurationswerte entgegen. */
    private final EdgeConfigService edgeConfigService;

    /** Verwaltet TTL-Policies für Cache-Präfixe. */
    private final TtlPolicyService ttlPolicyService;

    /** Stellt Cache-Inhalte wieder her. */
    private final EdgeFileService edgeFileService;

    /**
     * Erstellt die Bootstrap-Komponente für die Wiederherstellung.
     *
     * @param runtimeStateStore Zugriff auf persistierte Laufzeitdaten
     * @param edgeConfigService Service für Runtime-Konfiguration
     * @param ttlPolicyService Service für TTL-Policies
     * @param edgeFileService Service für Cache-Wiederherstellung
     */
    public EdgeRecoveryBootstrap(
            EdgeRuntimeStateStore runtimeStateStore,
            EdgeConfigService edgeConfigService,
            TtlPolicyService ttlPolicyService,
            EdgeFileService edgeFileService) {
        this.runtimeStateStore = runtimeStateStore;
        this.edgeConfigService = edgeConfigService;
        this.ttlPolicyService = ttlPolicyService;
        this.edgeFileService = edgeFileService;
    }

    /** Lädt den gespeicherten Zustand nach dem Start und übernimmt ihn in den Arbeitsspeicher. */
    @PostConstruct
    public void restoreOnStartup() {
        EdgeRuntimeStateStore.RestoredState restored = runtimeStateStore.load();
        if (restored != null) {
            edgeConfigService.update(restored.config());
            ttlPolicyService.clear();
            for (Map.Entry<String, Long> e : restored.ttlPolicies().entrySet()) {
                ttlPolicyService.setPrefixTtlMs(e.getKey(), e.getValue());
            }
            log.info(
                    "Wiederhergestellt: Runtime-Konfiguration und {} TTL-Policies",
                    restored.ttlPolicies().size());
        }
        edgeFileService.restoreCacheFromDisk();
    }
}
