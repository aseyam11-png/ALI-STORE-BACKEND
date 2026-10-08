package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.pricing.dto.PromotionRequest;
import com.store.pricing.dto.PromotionResponse;
import com.store.pricing.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromotionController {
    private final PromotionService service;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<java.util.List<PromotionResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(service.getActivePromotions(false), "Promotions retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PromotionResponse>> create(@Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request), "Promotion created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromotionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request), "Promotion updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}