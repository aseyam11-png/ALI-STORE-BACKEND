package com.store.pricing.controller;

import com.store.common.response.ApiResponse;
import com.store.pricing.dto.PromotionResponse;
import com.store.pricing.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {
    private final PromotionService service;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getActive(
            @RequestParam(defaultValue = "false") boolean flashOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getActivePromotions(flashOnly),
                "Active promotions retrieved successfully"));
    }
}