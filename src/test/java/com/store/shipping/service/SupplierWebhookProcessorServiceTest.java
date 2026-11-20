package com.store.shipping.service;

import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SupplierWebhookProcessorServiceTest {

    @Test
    void duplicateEventIsReadOnlyAndDoesNotApplyShipmentUpdateTwice() {
        SupplierWebhookEventService eventService = mock(SupplierWebhookEventService.class);
        ShipmentService shipmentService = mock(ShipmentService.class);
        SupplierWebhookProcessorService processor =
                new SupplierWebhookProcessorService(eventService, shipmentService);

        SupplierShipmentUpdateRequest request = new SupplierShipmentUpdateRequest();
        request.setSupplierCode("alpha");
        request.setSupplierOrderId("SUP-100");
        request.setStatus(com.store.shipping.entity.ShipmentStatus.IN_TRANSIT);

        ShipmentResponse existing = ShipmentResponse.builder()
                .id(7L)
                .orderId(11L)
                .build();

        when(eventService.recordIfNew("alpha", "evt-1")).thenReturn(false);
        when(shipmentService.getBySupplierOrderIdForSupplier("alpha", "SUP-100")).thenReturn(existing);

        ShipmentResponse result =
                processor.process("alpha", "evt-1", request);

        assertEquals(existing, result);
        verify(shipmentService).getBySupplierOrderIdForSupplier("alpha", "SUP-100");
        verify(shipmentService, never()).getBySupplierOrderId(anyString());
        verify(shipmentService, never()).applySupplierUpdate(
                anyString(), anyString(), any(), any(), any(), any());
    }
}
