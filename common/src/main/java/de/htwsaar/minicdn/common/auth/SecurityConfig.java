package de.htwsaar.minicdn.common.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Value("${minicdn.admin.token:secret-token}")
    private String adminToken;

    @Bean
    public AdminAuthFilter adminAuthFilter() {
        return new AdminAuthFilter(adminToken);
    }
}
