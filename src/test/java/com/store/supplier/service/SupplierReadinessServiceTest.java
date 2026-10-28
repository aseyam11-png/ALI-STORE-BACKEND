package com.store.supplier.service;

import com.store.common.exception.BusinessException;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierReadinessServiceTest {

    private SupplierCredentialResolver credentialResolver;
    private SupplierReadinessService readinessService;

    @BeforeEach
    void setUp() {
        credentialResolver = mock(SupplierCredentialResolver.class);
        readinessService = new SupplierReadinessService(credentialResolver);
        ReflectionTestUtils.setField(readinessService, "fulfillmentEnabled", true);
    }

    @Test
    void assertReady_acceptsOnlyFullyConfiguredActiveSupplier() {
        Supplier supplier = readySupplier();
        when(credentialResolver.resolveFulfillmentToken(supplier)).thenReturn("test-token");

        assertDoesNotThrow(() -> readinessService.assertReady(supplier));
    }

    @Test
    void assertReady_rejectsWhenGlobalFulfillmentIsDisabled() {
        ReflectionTestUtils.setField(readinessService, "fulfillmentEnabled", false);
        Supplier supplier = readySupplier();

        assertThrows(BusinessException.class, () -> readinessService.assertReady(supplier));
    }

    @Test
    void assertReady_rejectsDisabledSupplier() {
        Supplier supplier = readySupplier();
        supplier.setStatus(SupplierStatus.INACTIVE);

        assertThrows(BusinessException.class, () -> readinessService.assertReady(supplier));
    }

    @Test
    void assertReady_rejectsMissingEndpointOrCredential() {
        Supplier withoutEndpoint = readySupplier();
        withoutEndpoint.setApiBaseUrl(" ");
        assertThrows(BusinessException.class, () -> readinessService.assertReady(withoutEndpoint));

        Supplier withoutCredential = readySupplier();
        when(credentialResolver.resolveFulfillmentToken(withoutCredential)).thenReturn(" ");
        assertThrows(BusinessException.class, () -> readinessService.assertReady(withoutCredential));
    }

    private Supplier readySupplier() {
        return Supplier.builder()
                .externalCode("READY-TEST")
                .name("Ready Test Supplier")
                .status(SupplierStatus.ACTIVE)
                .fulfillmentEnabled(true)
                .inventorySyncEnabled(true)
                .apiBaseUrl("https://supplier.example")
                .build();
    }
}
