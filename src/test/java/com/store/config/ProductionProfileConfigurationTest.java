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
    void productionProfileRequiresHttpsAndAdminGateConfiguration() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application-prod.yml"));

        assertTrue(yaml.contains("require-https: true"));
        assertTrue(yaml.contains("admin-gate:"));
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("ADMIN_GATE_PASSWORD_HASH"));
        assertTrue(yaml.contains("TURNSTILE_SECRET_KEY"));
        assertTrue(yaml.contains("TURNSTILE_HOSTNAME"));
    }

    @Test
    void adminGateMigrationUsesUnusedVersionAfterExistingBaseMigrations() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V22__admin_gate_verified_otp.sql");
        assertTrue(Files.exists(migration));
        String sql = Files.readString(migration);
        assertTrue(sql.contains("admin_gate_verified"));
        assertTrue(Files.exists(Path.of(
                "src/main/resources/db/migration/V20__supplier_product_identifiers_and_store_skus.sql")));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/db/migration/V17__admin_gate_verified_otp_and_security.sql")));
    }
}
