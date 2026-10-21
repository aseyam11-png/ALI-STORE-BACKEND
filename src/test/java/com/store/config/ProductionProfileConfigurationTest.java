
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

        // Normalize line endings so the assertions work on Windows and Unix.
        yaml = yaml.replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(
                yaml.contains("profiles:\n    default: dev"),
                "application.yml must declare dev as the default profile");

        assertFalse(
                yaml.contains("profiles:\n    active: dev"),
                "application.yml must not force dev as the active profile");
    }

    @Test
    void productionProfileExistsAsExplicitDeploymentConfiguration() {
        assertTrue(Files.exists(
                Path.of("src/main/resources/application-prod.yml")));
    }
}
