package com.store.shipping.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.response.ApiResponse;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import com.store.shipping.service.SupplierWebhookProcessorService;
import com.store.supplier.service.SupplierCredentialResolver;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/v1/integrations/suppliers")
@RequiredArgsConstructor
public class SupplierShipmentWebhookController {

    private static final int MAX_SUPPLIER_CODE_LENGTH = 80;
    private static final int MAX_EVENT_ID_LENGTH = 150;

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final SupplierCredentialResolver credentialResolver;
    private final SupplierWebhookProcessorService processorService;

    @Value("${app.fulfillment.webhook-max-skew-seconds:300}")
    private long maxSkewSeconds;

    @PostMapping("/shipments/update")
    public ResponseEntity<ApiResponse<ShipmentResponse>> updateShipment(
            @RequestHeader(name = "X-Supplier-Event-Id", required = false) String eventId,
            @RequestHeader(name = "X-Supplier-Timestamp", required = false) String timestampHeader,
            @RequestHeader(name = "X-Supplier-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        SupplierShipmentUpdateRequest request;
        try {
            request = objectMapper.readValue(rawBody, SupplierShipmentUpdateRequest.class);
        } catch (JsonProcessingException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid supplier webhook payload"));
        }

        var validationErrors = validator.validate(request);
        if (!validationErrors.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid supplier webhook payload"));
        }

        String supplierCode = request.getSupplierCode().trim();
        String normalizedEventId = eventId == null ? null : eventId.trim();

        if (eventId == null || eventId.isBlank()
                || timestampHeader == null || timestampHeader.isBlank()
                || signature == null || signature.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing supplier webhook authentication headers"));
        }

        if (supplierCode.length() > MAX_SUPPLIER_CODE_LENGTH
                || normalizedEventId == null
                || normalizedEventId.length() > MAX_EVENT_ID_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            "Supplier webhook identifiers exceed the supported length"));
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid supplier webhook timestamp"));
        }

        long skew = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (skew > maxSkewSeconds) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Supplier webhook timestamp is outside the allowed window"));
        }

        String secret = credentialResolver.resolveWebhookSecret(supplierCode);
        if (secret.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Supplier webhook is not configured"));
        }

        String expected;
        try {
            expected = sign(secret, normalizedEventId + "." + timestamp + "." + rawBody);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Supplier webhook verification failed"));
        }

        String provided = signature.startsWith("sha256=")
                ? signature.substring("sha256=".length()) : signature;

        if (!constantTimeEquals(expected, provided)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid supplier webhook signature"));
        }

        ShipmentResponse response = processorService.process(
                supplierCode, normalizedEventId, request);

        return ResponseEntity.ok(ApiResponse.success(response, "Supplier shipment update applied"));
    }

    private String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) return false;
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
