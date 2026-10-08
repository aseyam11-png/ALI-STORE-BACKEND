package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.shipping.dto.ShippingMethodRequest;
import com.store.shipping.dto.ShippingMethodResponse;
import com.store.shipping.service.ShippingMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/shipping/methods")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShippingMethodController {

    private final ShippingMethodService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShippingMethodResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(
                service.getAllAdmin(), "Shipping methods retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShippingMethodResponse>> create(
            @Valid @RequestBody ShippingMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.create(request), "Shipping method created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShippingMethodResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ShippingMethodRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.update(id, request), "Shipping method updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}