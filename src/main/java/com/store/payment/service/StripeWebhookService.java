package com.store.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.entity.Order;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.order.service.OrderService;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.provider.StripeCurrency;
import com.store.payment.service.RefundService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("prod")
public class StripeWebhookService {

    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final PaymentReservationService reservationService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final RefundService refundService;
    private final EntityManager entityManager;

    @Value("${app.payment.stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookService(
            ObjectMapper objectMapper,
            PaymentRepository paymentRepository,
            PaymentReservationService reservationService,
            OrderRepository orderRepository,
            OrderService orderService,
            RefundService refundService,
            EntityManager entityManager) {
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
        this.reservationService = reservationService;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.refundService = refundService;
        this.entityManager = entityManager;
    }

    @Transactional
    public void process(String payload, String signatureHeader) {
        verifySignature(payload, signatureHeader);

        final JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Stripe webhook JSON", ex);
        }

        String type = event.path("type").asText("");
        if (!"checkout.session.completed".equals(type)
                && !"checkout.session.expired".equals(type)) {
            return;
        }
        if (!event.path("livemode").asBoolean(false)) {
            throw new IllegalArgumentException("Test-mode Stripe events cannot settle production payments");
        }

        JsonNode object = event.path("data").path("object");
        String sessionId = object.path("id").asText(null);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Stripe checkout session id is missing");
        }

        Long metadataOrderId = object.path("metadata").path("order_id").asLong(0L);
        Long orderId = metadataOrderId > 0 ? metadataOrderId : null;
        if (orderId == null) {
            String reference = object.path("client_reference_id").asText(null);
            if (reference != null && reference.matches("\\d+")) {
                orderId = Long.valueOf(reference);
            }
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Stripe checkout order reference is missing");
        }

        Payment payment = paymentRepository.findByTransactionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Stripe payment session is not registered"));

        if (!payment.getOrderId().equals(orderId)
                || !sessionId.equals(payment.getTransactionId())) {
            throw new IllegalArgumentException("Stripe checkout session does not match the registered payment attempt");
        }

        if ("checkout.session.completed".equals(type)
                && "paid".equalsIgnoreCase(object.path("payment_status").asText(""))) {
            if (payment.getStatus() != PaymentStatus.PENDING
                    && payment.getStatus() != PaymentStatus.COMPLETED) {
                return;
            }
            if (payment.getMethod() != PaymentMethod.CREDIT_CARD) {
                throw new IllegalArgumentException("Stripe webhook cannot settle a non-card payment");
            }

            Order order = orderRepository.findByIdForUpdate(payment.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found for Stripe payment"));
            long webhookAmount = object.path("amount_total").asLong(-1L);
            String webhookCurrency = object.path("currency").asText("");
            if (webhookAmount < 0 || !webhookCurrency.equalsIgnoreCase(order.getCurrency())) {
                throw new IllegalArgumentException("Stripe checkout amount or currency is missing");
            }
            long expectedAmount = StripeCurrency.toMinorUnits(order.getTotalAmount(), order.getCurrency());
            if (webhookAmount != expectedAmount) {
                throw new IllegalArgumentException("Stripe checkout amount does not match the order total");
            }

            if (payment.getStatus() == PaymentStatus.PENDING) {
                reservationService.markCompleted(payment.getId(), sessionId);
            }
            entityManager.clear();

            Order currentOrder = orderRepository.findByIdForUpdate(payment.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found for Stripe payment"));
            if (currentOrder.getStatus() == OrderStatus.PENDING
                    && orderService.expireIfReservationElapsed(currentOrder)) {
                entityManager.clear();
                currentOrder = orderRepository.findByIdForUpdate(payment.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found after reservation expiry"));
            }

            if (currentOrder.getStatus() == OrderStatus.CANCELLED) {
                entityManager.clear();
                refundService.prepareOrderCancellationRefund(
                        currentOrder.getId(),
                        "Stripe payment completed after order cancellation or reservation expiry");
                return;
            }

            if (currentOrder.getStatus() == OrderStatus.PENDING) {
                orderService.updateOrderStatus(currentOrder.getId(), OrderStatus.PROCESSING);
            }
            return;
        }

        if ("checkout.session.expired".equals(type)
                && payment.getStatus() == PaymentStatus.PENDING) {
            reservationService.markFailed(payment.getId(), "Stripe Checkout session expired");
        }
    }

    private void verifySignature(String payload, String header) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("Missing Stripe-Signature header");
        }

        long timestamp = 0;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid Stripe webhook timestamp", ex);
                }
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }

        if (timestamp == 0 || signatures.isEmpty()) {
            throw new IllegalArgumentException("Invalid Stripe-Signature header");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > 300) {
            throw new IllegalArgumentException("Stripe webhook timestamp is outside the allowed window");
        }

        String signedPayload = timestamp + "." + payload;
        String actual = hmacSha256Hex(webhookSecret, signedPayload);

        boolean valid = signatures.stream()
                .anyMatch(expected -> MessageDigest.isEqual(
                        actual.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8)));

        if (!valid) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature");
        }
    }

    private String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not verify Stripe webhook signature", ex);
        }
    }
}