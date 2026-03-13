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

/** Applies persisted state on startup.
 * Restores configuration and TTL policies
 * from {@link EdgeRuntimeStateStore} so the edge resumes previous runtime behavior
 * after restart.
 */
@Component
@Profile("edge")
public class EdgeRecoveryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EdgeRecoveryBootstrap.class);

    /** Provides access to persisted runtime state for the edge node. */
    private final EdgeRuntimeStateStore runtimeStateStore;

    /** Service to apply restored edge configuration settings. */
    private final EdgeConfigService edgeConfigService;

    /** Service to manage TTL policies for cached content prefixes. */
    private final TtlPolicyService ttlPolicyService;

    /** Service to restore cache content. */
    private final EdgeFileService edgeFileService;

    /**
     * Creates a recovery bootstrap that can restore edge runtime state.
     * @param runtimeStateStore store for persisted runtime state
     * @param edgeConfigService service to update edge configuration
     * @param ttlPolicyService service to manage TTL policies
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

    /**
     * Loads persisted state on startup and applies configuration and TTL policies
     * so the edge resumes its previous runtime behavior.
     */
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
                    "Recovered edge runtime config and {} TTL policies",
                    restored.ttlPolicies().size());
        }
        edgeFileService.restoreCacheFromDisk();
    }
}
