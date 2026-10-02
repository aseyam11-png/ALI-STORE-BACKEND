package com.store.pricing.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final PricingService pricingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PricingRuleResponse>>> getAll(org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                pricingService.getAll(pageable),
                "Pricing rules retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PricingRuleResponse>> create(@Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                pricingService.create(request),
                "Pricing rule created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                pricingService.update(id, request),
                "Pricing rule updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        pricingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Pricing rule deleted successfully"));
    }

    @GetMapping("/preview/{productId}")
    public ResponseEntity<ApiResponse<BigDecimal>> preview(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(
                pricingService.calculateSellingPricePreview(productId),
                "Price preview calculated successfully"));
    }
}
