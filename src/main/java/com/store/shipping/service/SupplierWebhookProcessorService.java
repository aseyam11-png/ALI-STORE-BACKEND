package com.store.shipping.service;

import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierWebhookProcessorService {

    private final SupplierWebhookEventService eventService;
    private final ShipmentService shipmentService;

    @Transactional
    public ShipmentResponse process(
            String supplierCode,
            String eventId,
            SupplierShipmentUpdateRequest request) {

        if (!eventService.recordIfNew(supplierCode, eventId)) {
            return shipmentService.getBySupplierOrderId(request.getSupplierOrderId().trim());
        }

        return shipmentService.applySupplierUpdate(
                supplierCode,
                request.getSupplierOrderId().trim(),
                request.getStatus(),
                request.getTrackingNumber(),
                request.getCarrier(),
                request.getEstimatedDeliveryDate());
    }
}
