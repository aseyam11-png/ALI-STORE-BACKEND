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
    void productionProfileExistsAsExplicitDeploymentConfiguration() throws Exception {
        Path path = Path.of("src/main/resources/application-prod.yml");
        assertTrue(Files.exists(path));
        String yaml = Files.readString(path);
        assertTrue(yaml.contains("require-https: true"));
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("ADMIN_GATE_PASSWORD_HASH"));
        assertTrue(yaml.contains("TURNSTILE_SECRET_KEY"));
        assertTrue(yaml.contains("TURNSTILE_HOSTNAME"));
    }
}
