package com.store.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionSecurityConfigContractTest {

    @Test
    void productionRequiresExplicitCorsOriginAndAdministratorFactors() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        PropertySource<?> production = sources.get(0);

        assertEquals("${CORS_ALLOWED_ORIGINS}",
                production.getProperty("app.cors.allowed-origins"));
        assertEquals("${ADMIN_GATE_PASSWORD_HASH}",
                production.getProperty("app.auth.admin-gate-password-hash"));
        assertEquals("${CLOUDFLARE_TURNSTILE_SECRET_KEY}",
                production.getProperty("app.auth.turnstile-secret-key"));
    }
}
