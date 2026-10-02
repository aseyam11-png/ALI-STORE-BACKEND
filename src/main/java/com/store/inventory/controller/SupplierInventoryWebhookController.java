package com.store.inventory.controller;

import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.ApiResponse;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.SupplierInventoryUpdateRequest;
import com.store.inventory.entity.Inventory;
import com.store.inventory.repository.InventoryRepository;
import com.store.inventory.service.InventoryService;
import com.store.pricing.service.PricingRuleService;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
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
    private final PricingRuleService pricingRuleService;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    @Value("${app.fulfillment.webhook-token:}")
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

        Product product = productRepository.findBySupplierSku(request.getSupplierSku().trim())
                .orElseThrow(() -> new ResourceNotFoundException("No product is mapped to supplier SKU: " + request.getSupplierSku()));

        if (product.getSupplier() == null
                || !product.getSupplier().getExternalCode().equals(request.getSupplierCode().trim())) {
            throw new ForbiddenException("Supplier is not authorized for this product mapping");
        }

        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not initialized for product: " + product.getId()));

        if (!Boolean.TRUE.equals(product.getSupplier().getInventorySyncEnabled())) {
            throw new ForbiddenException("Supplier inventory synchronization is disabled");
        }

        if (request.getSupplierPrice() != null && request.getSupplierPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            product.setSupplierCost(request.getSupplierPrice());
            productRepository.save(product);
            pricingRuleService.recalculateProduct(product.getId());
        }

        InventoryResponse response = inventoryService.syncSupplierStock(
                product.getId(), request.getQuantity(),
                request.getSyncedAt() != null ? request.getSyncedAt() : LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success(response, "Supplier inventory synchronized"));
    }
}
