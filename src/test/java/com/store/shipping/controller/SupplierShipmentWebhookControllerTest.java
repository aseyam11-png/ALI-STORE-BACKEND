package com.store.shipping.controller;

import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.service.SupplierWebhookProcessorService;
import com.store.supplier.service.SupplierCredentialResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierShipmentWebhookControllerTest {

    @Mock private Validator validator;
    @Mock private SupplierCredentialResolver credentialResolver;
    @Mock private SupplierWebhookProcessorService processorService;

    private SupplierShipmentWebhookController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = new SupplierShipmentWebhookController(
                new ObjectMapper(), validator, credentialResolver, processorService);
        ReflectionTestUtils.setField(controller, "maxSkewSeconds", 300L);
        when(validator.validate(any())).thenReturn(Set.of());
    }

    @Test
    void rejectsOversizedEventIdBeforeHmacLookup() {
        String eventId = "x".repeat(151);

        ResponseEntity<?> response = controller.updateShipment(
                eventId,
                String.valueOf(Instant.now().getEpochSecond()),
                "sha256=invalid",
                body());

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(credentialResolver, processorService);
    }

    @Test
    void rejectsStaleTimestampBeforeHmacLookup() {
        ResponseEntity<?> response = controller.updateShipment(
                "evt-stale",
                String.valueOf(Instant.now().minusSeconds(301).getEpochSecond()),
                "sha256=invalid",
                body());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(credentialResolver, processorService);
    }

    @Test
    void acceptsValidSignatureAndDelegatesToProcessor() throws Exception {
        String eventId = "evt-valid";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String secret = "supplier-secret";

        when(credentialResolver.resolveWebhookSecret("alpha"))
                .thenReturn(secret);
        when(processorService.process(
                eq("alpha"),
                eq(eventId),
                any()))
                .thenReturn(ShipmentResponse.builder()
                        .id(7L)
                        .orderId(12L)
                        .status(ShipmentStatus.IN_TRANSIT)
                        .build());

        String signature = sign(
                secret,
                eventId + "." + timestamp + "." + body());

        ResponseEntity<?> response = controller.updateShipment(
                eventId, timestamp, "sha256=" + signature, body());

        assertEquals(200, response.getStatusCode().value());
        verify(processorService).process(
                eq("alpha"), eq(eventId), any());
    }

    private String body() {
        return """
                {
                  "supplierCode": "alpha",
                  "supplierOrderId": "SUP-100",
                  "status": "IN_TRANSIT"
                }
                """;
    }

    private String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
