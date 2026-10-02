package com.store.inventory.controller;

import com.store.common.response.ApiResponse;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.SupplierInventoryUpdateRequest;
import com.store.inventory.service.InventoryService;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/integrations/suppliers")
@RequiredArgsConstructor
public class SupplierInventoryWebhookController {

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    @Value("\${app.fulfillment.webhook-token:}")
    private String webhookToken;

    @PostMapping("/inventory/update")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateInventory(
            @RequestHeader(name = "X-Supplier-Webhook-Token", required = false) String providedToken,
            @Valid @RequestBody SupplierInventoryUpdateRequest request) {

        if (webhookToken == null || webhookToken.isBlank()
                || providedToken == null
                || !MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid supplier webhook token"));
        }

        Supplier supplier = supplierRepository.findByExternalCode(request.getSupplierCode().trim())
                .orElseThrow(() -> new com.store.common.exception.ResourceNotFoundException("Supplier not found"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new com.store.common.exception.ResourceNotFoundException("Product not found"));

        if (product.getSupplier() == null || !product.getSupplier().getId().equals(supplier.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Supplier is not authorized for this product"));
        }

        if (product.getSupplierSku() == null || !product.getSupplierSku().equals(request.getSupplierSku().trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Supplier SKU does not match the product mapping"));
        }

        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.syncSupplierStock(product.getId(), request.getQuantity(), LocalDateTime.now()),
                "Supplier inventory synchronized"));
    }
}
