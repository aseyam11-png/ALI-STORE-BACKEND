package com.store.pricing.controller;

import com.store.common.response.ApiResponse;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PricingAdminController {

    private final PricingService pricingService;

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> getRules() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getAllRules(), "Pricing rules retrieved successfully"));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> createRule(
            @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pricingService.createRule(request), "Pricing rule created successfully"));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.updateRule(id, request), "Pricing rule updated successfully"));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        pricingService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reprice/product/{productId}")
    public ResponseEntity<ApiResponse<BigDecimal>> repriceProduct(@PathVariable Long productId) {
        // Repricing is persisted here because the admin is explicitly invoking
        // the pricing policy for this product.
        return ResponseEntity.ok(ApiResponse.success(
                pricingService.repriceAndSave(productId),
                "Product repriced successfully"));
    }
}
