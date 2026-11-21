package com.store.shipping.service;

import com.store.common.exception.ResourceNotFoundException;
import com.store.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ShipmentServiceSupplierReadTest {

    @Test
    void supplierScopedLookupDoesNotReturnAnotherSuppliersShipment() {
        ShipmentRepository repository = mock(ShipmentRepository.class);
        ShipmentService service = new ShipmentService(repository, null, null, null, null);

        when(repository.findBySupplierOrderIdAndSupplierCode("SUP-B-100", "supplier-alpha"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getBySupplierOrderIdForSupplier("supplier-alpha", "SUP-B-100"));

        verify(repository).findBySupplierOrderIdAndSupplierCode("SUP-B-100", "supplier-alpha");
        verify(repository, never()).findBySupplierOrderId(anyString());
    }
}
