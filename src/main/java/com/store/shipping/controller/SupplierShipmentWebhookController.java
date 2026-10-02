package com.store.shipping.controller;

import com.store.common.response.ApiResponse;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import com.store.shipping.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/integrations/suppliers")
@RequiredArgsConstructor
public class SupplierShipmentWebhookController {

    private final ShipmentService shipmentService;

    @Value("${app.fulfillment.webhook-token:}")
    private String webhookToken;

    @PostMapping("/shipments/update")
    public ResponseEntity<ApiResponse<ShipmentResponse>> updateShipment(
            @RequestHeader(name = "X-Supplier-Webhook-Token", required = false) String providedToken,
            @Valid @RequestBody SupplierShipmentUpdateRequest request) {

        if (webhookToken == null || webhookToken.isBlank()
                || providedToken == null
                || !MessageDigest.isEqual(
                        webhookToken.getBytes(StandardCharsets.UTF_8),
                        providedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid supplier webhook token"));
        }

        ShipmentResponse response = shipmentService.applySupplierUpdate(
                request.getSupplierCode().trim(),
                request.getSupplierOrderId().trim(),
                request.getStatus(),
                request.getTrackingNumber(),
                request.getCarrier(),
                request.getEstimatedDeliveryDate());

        return ResponseEntity.ok(ApiResponse.success(response, "Supplier shipment update applied"));
    }
}
