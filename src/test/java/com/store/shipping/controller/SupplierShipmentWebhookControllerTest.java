package com.store.shipping.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.dto.SupplierShipmentUpdateRequest;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.service.SupplierWebhookProcessorService;
import com.store.supplier.service.SupplierCredentialResolver;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierShipmentWebhookControllerTest {

    @Mock
    private Validator validator;

    @Mock
    private SupplierCredentialResolver credentialResolver;

    @Mock
    private SupplierWebhookProcessorService processorService;

    private SupplierShipmentWebhookController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new SupplierShipmentWebhookController(
                objectMapper = new ObjectMapper(),
                validator,
                credentialResolver,
                processorService);
        ReflectionTestUtils.setField(controller, "maxSkewSeconds", 300L);
        when(validator.validate(any())).thenReturn(Set.of());
    }

    @Test
    void rejectsMissingWebhookAuthenticationHeaders() {
        ResponseEntity<?> response = controller.updateShipment(
                null, null, null,
                "{"supplierCode":"alpha","supplierOrderId":"SUP-1","status":"IN_TRANSIT"}");

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(credentialResolver, processorService);
    }

    @Test
    void rejectsStaleTimestampBeforeSupplierCredentialLookup() {
        String body = body();
        String timestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());

        ResponseEntity<?> response = controller.updateShipment(
                "evt-stale", timestamp, "sha256=anything", body);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(credentialResolver, processorService);
    }

    @Test
    void rejectsInvalidHmacSignature() {
        when(credentialResolver.resolveWebhookSecret("alpha"))
                .thenReturn("supplier-secret");

        ResponseEntity<?> response = controller.updateShipment(
                "evt-2",
                String.valueOf(Instant.now().getEpochSecond()),
                "sha256=not-valid",
                body());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(processorService);
    }

    @Test
    void acceptsValidHmacAndDelegatesExactlyOnce() throws Exception {
        String body = body();
        String eventId = "evt-valid";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String secret = "supplier-secret";

        when(credentialResolver.resolveWebhookSecret("alpha"))
                .thenReturn(secret);

        ShipmentResponse expected = ShipmentResponse.builder()
                .id(55L)
                .orderId(99L)
                .status(ShipmentStatus.IN_TRANSIT)
                .build();
        when(processorService.process(
                eq("alpha"), eq(eventId), any(SupplierShipmentUpdateRequest.class)))
                .thenReturn(expected);

        String signature = sign(
                secret, eventId + "." + timestamp + "." + body);

        ResponseEntity<?> response = controller.updateShipment(
                eventId, timestamp, "sha256=" + signature, body);

        assertEquals(200, response.getStatusCode().value());
        verify(processorService).process(
                eq("alpha"), eq(eventId), any(SupplierShipmentUpdateRequest.class));
    }

    private String body() {
        return """
                {
                  "supplierCode": "alpha",
                  "supplierOrderId": "SUP-1",
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
