package com.store.pricing.controller;

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

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PricingRuleController {
    private final PricingRuleService pricingRuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(pricingRuleService.findAll(), "Pricing rules retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PricingRuleResponse>> create(@Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pricingRuleService.create(request), "Pricing rule created successfully"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> update(@PathVariable Long id, @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pricingRuleService.update(id, request), "Pricing rule updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        pricingRuleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Pricing rule deleted successfully"));
    }
}