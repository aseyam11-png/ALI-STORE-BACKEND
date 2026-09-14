package com.store.shipping.controller;

import com.store.shipping.dto.ShipmentRequest;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody ShipmentRequest request) {
        return new ResponseEntity<>(shipmentService.createShipment(request), HttpStatus.CREATED);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ShipmentResponse> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(shipmentService.getShipmentByOrderId(orderId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ShipmentResponse> updateStatus(@PathVariable Long id, @RequestParam ShipmentStatus status) {
        return ResponseEntity.ok(shipmentService.updateStatus(id, status));
    }
}