package com.store.shipping.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.shipping.dto.ShipmentResponse;
import com.store.supplier.service.SupplierCredentialResolver;
import com.store.shipping.service.SupplierWebhookProcessorService;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import com.store.shipping.entity.ShipmentStatus;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupplierShipmentWebhookControllerTest {

    private SupplierWebhookProcessorService processorService;
    private SupplierShipmentWebhookController controller;

    @BeforeEach
    void setUp() {
        processorService = mock(SupplierWebhookProcessorService.class);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("SUPPLIER_WEBHOOK_TOKEN_ALPHA", "test-secret");
        environment.setActiveProfiles("test");

        SupplierCredentialResolver resolver = new SupplierCredentialResolver(environment);
        Validator validator = mock(Validator.class);
        when(validator.validate(any(SupplierShipmentUpdateRequest.class))).thenReturn(Set.of());

        controller = new SupplierShipmentWebhookController(
                new ObjectMapper(), validator, resolver, processorService);
        ReflectionTestUtils.setField(controller, "maxSkewSeconds", 300L);
    }

    @Test
    void rejectsOversizedSupplierCodeBeforeCredentialLookup() {
        String body = """
                {"supplierCode":"%s","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                """.formatted("x".repeat(81));

        var result = controller.updateShipment(
                "evt-oversized-supplier",
                Long.toString(Instant.now().getEpochSecond()),
                "sha256=invalid",
                body);

        assertEquals(400, result.getStatusCode().value());
        verifyNoInteractions(processorService);
    }

    @Test
    void rejectsOversizedEventIdBeforeCredentialLookup() {
        String body = """
                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                """;

        var result = controller.updateShipment(
                "x".repeat(151),
                Long.toString(Instant.now().getEpochSecond()),
                "sha256=invalid",
                body);

        assertEquals(400, result.getStatusCode().value());
        verifyNoInteractions(processorService);
    }

    @Test
    void acceptsValidHmacSignature() {
        String body = """
                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                """;
        long timestamp = Instant.now().getEpochSecond();
        String eventId = "evt-1";
        String signature = sign("test-secret", eventId + "." + timestamp + "." + body);

        ShipmentResponse response = ShipmentResponse.builder().id(7L).build();
        when(processorService.process(eq("alpha"), eq(eventId), any()))
                .thenReturn(response);

        var result = controller.updateShipment(
                eventId,
                Long.toString(timestamp),
                "sha256=" + signature,
                body);

        assertEquals(200, result.getStatusCode().value());
        verify(processorService).process(eq("alpha"), eq(eventId), any());
    }

    @Test
    void rejectsInvalidSignature() {
        String body = """
                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                """;
        long timestamp = Instant.now().getEpochSecond();

        var result = controller.updateShipment(
                "evt-2",
                Long.toString(timestamp),
                "sha256=bad",
                body);

        assertEquals(401, result.getStatusCode().value());
        verifyNoInteractions(processorService);
    }

    @Test
    void rejectsStaleTimestamp() {
        String body = """
                {"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}
                """;
        long timestamp = Instant.now().minusSeconds(600).getEpochSecond();
        String eventId = "evt-3";
        String signature = sign("test-secret", eventId + "." + timestamp + "." + body);

        var result = controller.updateShipment(
                eventId,
                Long.toString(timestamp),
                signature,
                body);

        assertEquals(401, result.getStatusCode().value());
        verifyNoInteractions(processorService);
    }

    private String sign(String secret, String payload) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
