package com.store.supplier.controller;

import com.store.common.response.ApiResponse;
import com.store.product.dto.ProductResponse;
import com.store.supplier.dto.SupplierProductPriceUpdateRequest;
import com.store.supplier.service.SupplierProductPriceSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/integrations/suppliers/products")
@RequiredArgsConstructor
public class SupplierProductPriceWebhookController {
    private final SupplierProductPriceSyncService syncService;

    @Value("${app.fulfillment.webhook-token:}")
    private String webhookToken;

    @PostMapping("/price-update")
    public ResponseEntity<ApiResponse<ProductResponse>> updatePrice(
            @RequestHeader(name = "X-Supplier-Webhook-Token", required = false) String providedToken,
            @Valid @RequestBody SupplierProductPriceUpdateRequest request) {

        if (webhookToken == null || webhookToken.isBlank()
                || providedToken == null
                || !MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid supplier webhook token"));
        }

        return ResponseEntity.ok(ApiResponse.success(
                syncService.synchronize(request),
                "Supplier price synchronized"));
    }
}
