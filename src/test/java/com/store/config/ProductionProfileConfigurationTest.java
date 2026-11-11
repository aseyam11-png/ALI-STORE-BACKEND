package com.store.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionProfileConfigurationTest {

    @Test
    void applicationYamlKeepsTheDevelopmentDefaultWithoutForcingItInProduction() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("profiles:\n    default: dev"));
        assertFalse(yaml.contains("profiles:\n    active: dev"));
        assertTrue(yaml.contains("admin-gate:"));
        assertTrue(yaml.contains("ADMIN_GATE_PASSWORD_HASH"));
        assertTrue(yaml.contains("TURNSTILE_SECRET_KEY"));
        assertTrue(yaml.contains("TURNSTILE_HOSTNAME"));
    }

    @Test
    void productionRequiresExplicitFrontendAuthRedirects() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertTrue(yaml.contains("frontend-success-redirect: ${FRONTEND_AUTH_SUCCESS_REDIRECT}"));
        assertTrue(yaml.contains("frontend-failure-redirect: ${FRONTEND_AUTH_FAILURE_REDIRECT}"));
        assertFalse(yaml.contains("frontend-success-redirect: ${FRONTEND_AUTH_SUCCESS_REDIRECT:"));
    }

    @Test
    void productionRequiresHttpsAndEnablesTheAdministratorGate() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertTrue(yaml.contains("require-https: true"));
        assertTrue(yaml.contains("admin-gate:"));
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("ADMIN_GATE_PASSWORD_HASH"));
        assertTrue(yaml.contains("TURNSTILE_SECRET_KEY"));
        assertTrue(yaml.contains("TURNSTILE_HOSTNAME"));
    }

    @Test
    void productionProfileExistsAsExplicitDeploymentConfiguration() {
        assertTrue(Files.exists(Path.of("src/main/resources/application-prod.yml")));
    }

    @Test
    void adminGateMigrationUsesTheNextUnusedVersionWithoutReusingOlderNumbers() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V22__admin_gate_verified_otp.sql");
        assertTrue(Files.exists(migration));
        String sql = Files.readString(migration);
        assertTrue(sql.contains("admin_gate_verified"));
        assertTrue(Files.exists(Path.of(
                "src/main/resources/db/migration/V21__store_currency_sar_default.sql")));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/db/migration/V17__admin_gate_verified_otp_and_security.sql")));
    }
}
