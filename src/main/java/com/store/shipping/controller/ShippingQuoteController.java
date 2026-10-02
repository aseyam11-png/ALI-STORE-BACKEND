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
            @RequestParam(defaultValue = "STANDARD") String method,
            @RequestParam(defaultValue = "USD") String currency) {
        var quote = shippingQuoteService.quote(subtotal, method);
        return ResponseEntity.ok(ApiResponse.success(
                ShippingQuoteResponse.builder()
                        .method(quote.method())
                        .amount(quote.amount())
                        .freeShippingThreshold(quote.freeShippingThreshold())
                        .currency(currency.toUpperCase())
                        .estimatedMinDays(quote.estimatedMinDays())
                        .estimatedMaxDays(quote.estimatedMaxDays())
                        .build(),
                "Shipping quote calculated"));
    }
}
