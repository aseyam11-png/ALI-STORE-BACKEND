package com.store.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.order.entity.Order;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.order.service.OrderService;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.service.PaymentReservationService;
import com.store.payment.service.RefundService;
import com.store.payment.service.StripeWebhookService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class StripeWebhookServiceTest {

    private static final String SECRET = "whsec_test_secret";

    private PaymentRepository paymentRepository;
    private PaymentReservationService reservationService;
    private OrderRepository orderRepository;
    private OrderService orderService;
    private RefundService refundService;
    private EntityManager entityManager;
    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        reservationService = mock(PaymentReservationService.class);
        orderRepository = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        refundService = mock(RefundService.class);
        entityManager = mock(EntityManager.class);

        service = new StripeWebhookService(
                new ObjectMapper(),
                paymentRepository,
                reservationService,
                orderRepository,
                orderService,
                refundService,
                entityManager);
        ReflectionTestUtils.setField(service, "webhookSecret", SECRET);
    }

    @Test
    void completedSession_requiresValidSignatureAndMatchingOrderAmount() throws Exception {
        Payment payment = Payment.builder()
                .id(7L)
                .orderId(42L)
                .amount(new BigDecimal("25.00"))
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PENDING)
                .transactionId("cs_test_123")
                .build();
        Order order = Order.builder()
                .id(42L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("25.00"))
                .currency("USD")
                .build();

        when(paymentRepository.findByTransactionId("cs_test_123")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));

        String payload = """
                {"id":"evt_test_123","livemode":true,"type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_123",
                  "amount_total":2500,
                  "currency":"usd",
                  "payment_status":"paid",
                  "client_reference_id":"42",
                  "metadata":{"order_id":"42"}
                }}}""";

        String header = signatureHeader(payload);

        assertDoesNotThrow(() -> service.process(payload, header));
        verify(reservationService).markCompleted(7L, "cs_test_123");
        verify(orderService).updateOrderStatus(42L, OrderStatus.PROCESSING);
    }

    @Test
    void paidSessionForCancelledOrderTriggersRefundInsteadOfFulfillment() throws Exception {
        Payment payment = Payment.builder()
                .id(9L)
                .orderId(55L)
                .amount(new BigDecimal("25.00"))
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PENDING)
                .transactionId("cs_late_123")
                .build();
        Order order = Order.builder()
                .id(55L)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("25.00"))
                .currency("USD")
                .build();

        when(paymentRepository.findByTransactionId("cs_late_123")).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(order));

        String payload = """
                {"id":"evt_late_123","livemode":true,"type":"checkout.session.completed","data":{"object":{
                  "id":"cs_late_123",
                  "amount_total":2500,
                  "currency":"usd",
                  "payment_status":"paid",
                  "client_reference_id":"55",
                  "metadata":{"order_id":"55"}
                }}}""";

        service.process(payload, signatureHeader(payload));

        verify(reservationService).markCompleted(9L, "cs_late_123");
        verify(refundService).prepareOrderCancellationRefund(
                55L, "Stripe payment completed after order cancellation or reservation expiry");
        verify(orderService, never()).updateOrderStatus(55L, OrderStatus.PROCESSING);
    }

    @Test
    void oldOrUnregisteredSessionCannotSettleByOrderIdFallback() throws Exception {
        String payload = """
                {"id":"evt_old_session","livemode":true,"type":"checkout.session.completed","data":{"object":{
                  "id":"cs_old_123",
                  "amount_total":2500,
                  "currency":"usd",
                  "payment_status":"paid",
                  "client_reference_id":"42",
                  "metadata":{"order_id":"42"}
                }}}""";

        when(paymentRepository.findByTransactionId("cs_old_123")).thenReturn(Optional.empty());

        assertThrows(com.store.common.exception.ResourceNotFoundException.class,
                () -> service.process(payload, signatureHeader(payload)));
        verify(paymentRepository).findByTransactionId("cs_old_123");
        verify(paymentRepository, never()).findByOrderId(anyLong());
        verifyNoInteractions(reservationService, orderRepository, orderService, refundService, entityManager);
    }

    @Test
    void testModeEventCannotSettleProductionPayment() throws Exception {
        String payload = """
                {"id":"evt_test_mode","livemode":false,"type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_123",
                  "amount_total":2500,
                  "currency":"usd",
                  "payment_status":"paid",
                  "client_reference_id":"42",
                  "metadata":{"order_id":"42"}
                }}}""";

        assertThrows(IllegalArgumentException.class,
                () -> service.process(payload, signatureHeader(payload)));
        verifyNoInteractions(paymentRepository, reservationService, orderRepository, orderService);
    }

    @Test
    void invalidSignature_isRejectedBeforePaymentLookup() throws Exception {
        String payload = """
                {"id":"evt_test_123","livemode":true,"type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_123"
                }}}""";

        assertThrows(IllegalArgumentException.class,
                () -> service.process(payload, "t=" + Instant.now().getEpochSecond() + ",v1=bad"));
        verifyNoInteractions(paymentRepository, orderRepository, reservationService, orderService, refundService, entityManager);
    }

    private String signatureHeader(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signed = timestamp + "." + payload;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }
}
