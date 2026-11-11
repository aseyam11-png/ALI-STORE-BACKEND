package com.store.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProfileConfigurationTest {

    @Test
    void applicationYamlDoesNotForceDevelopmentProfile() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml")).replace("\r\n", "\n");

        assertTrue(yaml.contains("profiles:\n    default: dev"));
        assertFalse(yaml.contains("profiles:\n    active: dev"));
    }

    @Test
    void productionRequiresExplicitFrontendAuthRedirects() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application-prod.yml"));

        assertTrue(yaml.contains("frontend-success-redirect: ${FRONTEND_AUTH_SUCCESS_REDIRECT}"));
        assertTrue(yaml.contains("frontend-failure-redirect: ${FRONTEND_AUTH_FAILURE_REDIRECT}"));
        assertFalse(yaml.contains("frontend-success-redirect: ${FRONTEND_AUTH_SUCCESS_REDIRECT:"));
    }

    @Test
    void productionProfileExistsAsExplicitDeploymentConfiguration() {
        assertTrue(Files.exists(
                Path.of("src/main/resources/application-prod.yml")));
    }
}
