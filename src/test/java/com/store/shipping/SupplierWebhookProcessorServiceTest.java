package com.store.shipping.service;

import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierWebhookProcessorServiceTest {

    @Mock private SupplierWebhookEventService eventService;
    @Mock private ShipmentService shipmentService;

    @Test
    void newEvent_appliesSupplierUpdateExactlyOnce() {
        SupplierWebhookProcessorService service =
                new SupplierWebhookProcessorService(eventService, shipmentService);

        SupplierShipmentUpdateRequest request = new SupplierShipmentUpdateRequest();
        request.setSupplierCode("alpha");
        request.setSupplierOrderId("SUP-1");
        request.setStatus(com.store.shipping.entity.ShipmentStatus.IN_TRANSIT);

        ShipmentResponse response = ShipmentResponse.builder().id(7L).build();
        when(eventService.recordIfNew("alpha", "evt-1")).thenReturn(true);
        when(shipmentService.applySupplierUpdate(
                "alpha", "SUP-1", request.getStatus(), null, null, null))
                .thenReturn(response);

        assertEquals(response, service.process("alpha", "evt-1", request));

        verify(shipmentService).applySupplierUpdate(
                "alpha", "SUP-1", request.getStatus(), null, null, null);
        verify(shipmentService, never()).getBySupplierOrderId(anyString());
    }

    @Test
    void duplicateEvent_returnsCurrentShipmentWithoutReapplyingTransition() {
        SupplierWebhookProcessorService service =
                new SupplierWebhookProcessorService(eventService, shipmentService);

        SupplierShipmentUpdateRequest request = new SupplierShipmentUpdateRequest();
        request.setSupplierCode("alpha");
        request.setSupplierOrderId("SUP-1");
        request.setStatus(com.store.shipping.entity.ShipmentStatus.DELIVERED);

        ShipmentResponse current = ShipmentResponse.builder().id(7L).build();
        when(eventService.recordIfNew("alpha", "evt-1")).thenReturn(false);
        when(shipmentService.getBySupplierOrderId("SUP-1")).thenReturn(current);

        assertEquals(current, service.process("alpha", "evt-1", request));

        verify(shipmentService).getBySupplierOrderId("SUP-1");
        verify(shipmentService, never()).applySupplierUpdate(
                anyString(), anyString(), any(), any(), any(), any());
    }
}
