package com.store.shipping.controller;

import com.store.common.response.ApiResponse;
import com.store.shipping.dto.ShippingQuoteResponse;
import com.store.shipping.service.ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingQuoteController {

    private final ShippingQuoteService shippingQuoteService;

    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<ShippingQuoteResponse>> quote(
            @RequestParam BigDecimal subtotal,
            @RequestParam(required = false, defaultValue = "STANDARD") String method) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingQuoteService.quote(subtotal, method),
                "Shipping quote calculated successfully"));
    }
}