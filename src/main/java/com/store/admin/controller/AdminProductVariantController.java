package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.product.dto.ProductVariantRequest;
import com.store.product.dto.ProductVariantAdminResponse;
import com.store.product.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/variants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductVariantController {
    private final ProductVariantService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductVariantAdminResponse>>> getAll(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(service.getAdminVariants(productId), "Product variants retrieved successfully"));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<ProductVariantAdminResponse>> create(@PathVariable Long productId, @Valid @RequestBody ProductVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(productId, request), "Product variant created successfully"));
    }
    @PutMapping("/{variantId}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> update(@PathVariable Long productId, @PathVariable Long variantId, @Valid @RequestBody ProductVariantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(productId, variantId, request), "Product variant updated successfully"));
    }
    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long variantId) {
        service.delete(productId, variantId);
        return ResponseEntity.noContent().build();
    }
}