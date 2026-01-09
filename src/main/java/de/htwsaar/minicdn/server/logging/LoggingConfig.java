package de.htwsaar.minicdn.server.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration für Logging und Tracing.
 */
@Configuration
public class LoggingConfig {

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
