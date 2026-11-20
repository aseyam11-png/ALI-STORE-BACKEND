package com.store.inventory.controller;

import com.store.common.response.ApiResponse;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.UpdateStockRequest;
import com.store.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getInventoryByProductId(productId),
                "Inventory retrieved successfully"));
    }

    @PutMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateStock(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.updateStock(productId, request),
                "Stock updated successfully"));
    }
}
