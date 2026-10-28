package com.store.supplier.service;

import com.store.common.exception.BusinessException;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierReadinessServiceTest {

    private SupplierRepository supplierRepository;
    private SupplierCredentialResolver credentialResolver;
    private SupplierReadinessService readinessService;

    @BeforeEach
    void setUp() {
        supplierRepository = mock(SupplierRepository.class);
        credentialResolver = mock(SupplierCredentialResolver.class);
        readinessService = new SupplierReadinessService(supplierRepository, credentialResolver);
        ReflectionTestUtils.setField(readinessService, "fulfillmentEnabled", true);
    }

    @Test
    void assertReady_acceptsOnlyFullyConfiguredActiveSupplier() {
        Supplier supplier = readySupplier();
        when(credentialResolver.resolveFulfillmentToken(supplier)).thenReturn("test-token");

        assertDoesNotThrow(() -> readinessService.assertReady(1L));
    }

    @Test
    void assertReady_rejectsWhenGlobalFulfillmentIsDisabled() {
        ReflectionTestUtils.setField(readinessService, "fulfillmentEnabled", false);
        Supplier supplier = readySupplier();

        assertThrows(BusinessException.class, () -> readinessService.assertReady(1L));
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
        assertThrows(BusinessException.class, () -> readinessService.assertReady(1L));

        Supplier withoutCredential = readySupplier();
        when(credentialResolver.resolveFulfillmentToken(withoutCredential)).thenReturn(" ");
        assertThrows(BusinessException.class, () -> readinessService.assertReady(1L));
    }

    private Supplier readySupplier() {
        Supplier supplier = Supplier.builder()
                .externalCode("READY-TEST")
                .name("Ready Test Supplier")
                .status(SupplierStatus.ACTIVE)
                .fulfillmentEnabled(true)
                .inventorySyncEnabled(true)
                .apiBaseUrl("https://supplier.example")
                .build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(credentialResolver.resolveFulfillmentToken(supplier)).thenReturn("test-token");
        return supplier;
    }
}
