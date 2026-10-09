package com.store.shipping.controller;

import com.store.common.response.ApiResponse;
import com.store.shipping.dto.ShippingMethodResponse;
import com.store.shipping.service.ShippingMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping/methods")
@RequiredArgsConstructor
public class ShippingMethodController {

    private final ShippingMethodService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShippingMethodResponse>>> getPublic(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getPublicMethods(country, currency),
                "Shipping methods retrieved successfully"));
    }
}