package de.htwsaar.minicdn.origin.config;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Service;

/**
 * Thread-sicherer Service für Live-Konfiguration des Origin.
 *
 * <p>Trennt fachliche Laufzeit-Konfiguration von HTTP/Transport-Details und
 * ermöglicht Änderungen ohne Neustart.</p>
 */
@Service
public class OriginRuntimeConfigService {

    private final AtomicReference<OriginRuntimeConfig> ref;
    private final Optional<LoggingSystem> loggingSystem;

    public OriginRuntimeConfigService(
            ObjectProvider<LoggingSystem> loggingSystemProvider,
            @Value("${origin.runtime.max-upload-bytes:0}") long maxUploadBytes,
            @Value("${origin.runtime.log-level:INFO}") String logLevel) {
        this.loggingSystem = Optional.ofNullable(loggingSystemProvider.getIfAvailable());
        this.ref =
                new AtomicReference<>(new OriginRuntimeConfig(Math.max(0, maxUploadBytes), normalizeLevel(logLevel)));
        applyLogLevel(this.ref.get().logLevel());
    }

    /** @return aktuelle Runtime-Konfiguration. */
    public OriginRuntimeConfig current() {
        return ref.get();
    }

    /**
     * Patcht die Runtime-Konfiguration atomar.
     *
     * @param maxUploadBytes neuer Max-Upload-Wert oder {@code null}
     * @param logLevel       neues Log-Level oder {@code null}
     * @return aktualisierte Konfiguration
     */
    public OriginRuntimeConfig patch(Long maxUploadBytes, String logLevel) {
        OriginRuntimeConfig updated = ref.updateAndGet(cur -> new OriginRuntimeConfig(
                maxUploadBytes != null ? Math.max(0, maxUploadBytes) : cur.maxUploadBytes(),
                logLevel != null ? normalizeLevel(logLevel) : cur.logLevel()));

        applyLogLevel(updated.logLevel());
        return updated;
    }

    private void applyLogLevel(String level) {
        loggingSystem.ifPresent(system -> system.setLogLevel("ROOT", LogLevel.valueOf(level)));
    }

    private static String normalizeLevel(String level) {
        String normalized = Objects.toString(level, "INFO").trim().toUpperCase();
        LogLevel.valueOf(normalized);
        return normalized;
    }
}
