package com.store.shipping.controller;

import com.store.shipping.dto.ShipmentRequest;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    // Creating a shipment and advancing its status are fulfillment operations, not
    // something a customer ever does - lock them to ADMIN (matching AdminOrderController).
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody ShipmentRequest request) {
        return new ResponseEntity<>(shipmentService.createShipment(request), HttpStatus.CREATED);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ShipmentResponse> getByOrderId(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(shipmentService.getShipmentByOrderId(userDetails.getUsername(), orderId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShipmentResponse> updateStatus(@PathVariable Long id, @RequestParam ShipmentStatus status) {
        return ResponseEntity.ok(shipmentService.updateStatus(id, status));
    }
}