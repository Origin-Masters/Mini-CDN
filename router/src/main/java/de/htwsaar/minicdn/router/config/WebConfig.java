package de.htwsaar.minicdn.router.config;

import de.htwsaar.minicdn.router.audit.AuditLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web-Konfiguration für Router-spezifische Interceptor.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditLoggingInterceptor auditLoggingInterceptor;

    public WebConfig(AuditLoggingInterceptor auditLoggingInterceptor) {
        this.auditLoggingInterceptor = auditLoggingInterceptor;
    }

    /**
     * Registriert den Audit-Interceptor für die CDN-API.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLoggingInterceptor).addPathPatterns("/api/cdn/**");
    }
}
