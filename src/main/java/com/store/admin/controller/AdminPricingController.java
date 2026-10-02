package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.pricing.dto.PricingRuleRequest;
import com.store.pricing.dto.PricingRuleResponse;
import com.store.pricing.service.PricingRuleService;
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
public class AdminPricingController {
    private final PricingRuleService pricingRuleService;

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> getRules() {
        return ResponseEntity.ok(ApiResponse.success(pricingRuleService.getAll(), "Pricing rules retrieved"));
    }

    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> create(@Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pricingRuleService.create(request), "Pricing rule created"));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> update(@PathVariable Long id,
                                                                     @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pricingRuleService.update(id, request), "Pricing rule updated"));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        pricingRuleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Pricing rule deleted"));
    }

    @PostMapping("/products/{id}/recalculate")
    public ResponseEntity<ApiResponse<BigDecimal>> recalculate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(pricingRuleService.recalculateProduct(id),
                "Selling price recalculated from supplier cost"));
    }
}
