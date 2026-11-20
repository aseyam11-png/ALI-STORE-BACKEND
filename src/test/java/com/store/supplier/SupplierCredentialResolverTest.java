package com.store.supplier;

import com.store.supplier.entity.Supplier;
import com.store.supplier.service.SupplierCredentialResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplierCredentialResolverTest {

    @Test
    void resolvesSupplierSpecificTokenBeforeSharedFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SUPPLIER_API_TOKEN_ALPHA_SUPPLIER", "alpha-token")
                .withProperty("app.inventory-sync.api-token", "shared-token");

        SupplierCredentialResolver resolver =
                new SupplierCredentialResolver(environment);

        Supplier supplier = Supplier.builder()
                .externalCode("alpha-supplier")
                .build();

        assertEquals("alpha-token", resolver.resolveApiToken(supplier));
    }

    @Test
    void nonProductionMayUseSharedFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.inventory-sync.api-token", "shared-token");
        environment.setActiveProfiles("test");

        SupplierCredentialResolver resolver =
                new SupplierCredentialResolver(environment);

        Supplier supplier = Supplier.builder()
                .externalCode("alpha-supplier")
                .build();

        assertEquals("shared-token", resolver.resolveApiToken(supplier));
    }

    @Test
    void webhookSecretIsSupplierSpecificAndHasNonProductionFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SUPPLIER_WEBHOOK_TOKEN_ALPHA_SUPPLIER", "alpha-hook")
                .withProperty("app.fulfillment.webhook-token", "shared-hook");
        environment.setActiveProfiles("test");

        SupplierCredentialResolver resolver =
                new SupplierCredentialResolver(environment);

        assertEquals(
                "alpha-hook",
                resolver.resolveWebhookSecret("alpha-supplier"));
        assertEquals(
                "shared-hook",
                resolver.resolveWebhookSecret("beta-supplier"));
    }

    @Test
    void fulfillmentTokenIsSupplierSpecificInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "app.fulfillment.api-tokens.alpha-supplier",
                        "alpha-fulfillment");
        environment.setActiveProfiles("prod");

        SupplierCredentialResolver resolver =
                new SupplierCredentialResolver(environment);

        Supplier supplier = Supplier.builder()
                .externalCode("alpha-supplier")
                .build();

        assertEquals(
                "alpha-fulfillment",
                resolver.resolveFulfillmentToken(supplier));
    }

    @Test
    void productionNeverUsesSharedFallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.inventory-sync.api-token", "shared-token");
        environment.setActiveProfiles("prod");

        SupplierCredentialResolver resolver =
                new SupplierCredentialResolver(environment);

        Supplier supplier = Supplier.builder()
                .externalCode("alpha-supplier")
                .build();

        assertEquals("", resolver.resolveApiToken(supplier));
    }
}
