package com.store.payment.controller;

import com.store.payment.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/payments/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService service;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {
        service.process(payload, signature);
        return ResponseEntity.ok().build();
    }
}