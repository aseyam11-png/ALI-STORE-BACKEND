package com.store.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProfileConfigurationTest {

    @Test
    void applicationYamlDoesNotForceDevelopmentProfile() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("profiles:\n    default: dev"));
        assertFalse(yaml.contains("profiles:\n    active: dev"));
    }

    @Test
    void productionProfileExistsAsExplicitDeploymentConfiguration() {
        assertTrue(Files.exists(
                Path.of("src/main/resources/application-prod.yml")));
    }

    @Test
    void productionProfileRequiresHttpsAndTheAdminSecurityGate() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application-prod.yml"));

        assertTrue(yaml.contains("require-https: true"));
        assertTrue(yaml.contains("cookie-secure: ${AUTH_COOKIE_SECURE:true}"));
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("password-hash: ${ADMIN_GATE_PASSWORD_HASH:}"));
        assertTrue(yaml.contains("turnstile-secret: ${TURNSTILE_SECRET_KEY:}"));
        assertTrue(yaml.contains("turnstile-hostname: ${TURNSTILE_HOSTNAME:}"));
    }
}
