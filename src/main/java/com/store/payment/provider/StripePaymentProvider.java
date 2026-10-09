package com.store.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.exception.BusinessException;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.payment.entity.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Profile("prod")
public class StripePaymentProvider implements PaymentProvider {

    private final RestClient stripeClient;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;

    @Value("${app.payment.stripe.secret-key}")
    private String secretKey;

    @Value("${app.payment.stripe.frontend-public-url}")
    private String frontendPublicUrl;

    public StripePaymentProvider(ObjectMapper objectMapper, OrderRepository orderRepository) {
        this.stripeClient = RestClient.builder().baseUrl("https://api.stripe.com").build();
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
    }

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        if (method != PaymentMethod.CREDIT_CARD) {
            return new PaymentResult(false, false, null, null,
                    "Configured Stripe provider supports CREDIT_CARD only");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found for payment checkout"));

        if (amount == null || amount.signum() <= 0
                || amount.compareTo(order.getTotalAmount()) != 0) {
            throw new BusinessException("Stripe amount must match the positive server order total");
        }
        long minorAmount = StripeCurrency.toMinorUnits(amount, order.getCurrency());
        String baseUrl = normalizeBaseUrl(frontendPublicUrl);
        String successUrl = baseUrl + "/buyer/orders/" + orderId
                + "/confirmation?payment=success&session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = baseUrl + "/buyer/orders/" + orderId
                + "/confirmation?payment=cancelled";

        String body =
                "mode=payment"
                + "&success_url=" + encode(successUrl)
                + "&cancel_url=" + encode(cancelUrl)
                + "&client_reference_id=" + encode(String.valueOf(orderId))
                + "&metadata%5Border_id%5D=" + encode(String.valueOf(orderId))
                + "&customer_email=" + encode(order.getUser().getEmail())
                + "&line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=" + encode(order.getCurrency().toLowerCase())
                + "&line_items%5B0%5D%5Bprice_data%5D%5Bproduct_data%5D%5Bname%5D="
                + encode("ALI STORE Order #" + orderId)
                + "&line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=" + minorAmount
                + "&line_items%5B0%5D%5Bquantity%5D=1";

        try {
            String raw = stripeClient.post()
                    .uri("/v1/checkout/sessions")
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(raw);
            String sessionId = json.path("id").asText(null);
            String url = json.path("url").asText(null);

            if (sessionId == null || sessionId.isBlank() || url == null || url.isBlank()) {
                throw new IllegalStateException("Stripe checkout response is missing session id or URL");
            }

            return new PaymentResult(false, true, sessionId, url, "Stripe Checkout session created");
        } catch (RestClientResponseException ex) {
            return new PaymentResult(false, false, null, null,
                    "Stripe rejected the checkout request: " + truncate(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            throw new IllegalStateException("Stripe checkout outcome could not be confirmed", ex);
        }
    }

    @Override
    public ChargeStatusResult getChargeStatus(Long orderId, String transactionId) {
        if (transactionId == null || !transactionId.matches("cs_[A-Za-z0-9_]+")) {
            return new ChargeStatusResult(false, false, transactionId,
                    "A registered Stripe Checkout session id is required for reconciliation");
        }

        JsonNode session = loadCheckoutSession(transactionId);
        Order order = requireMatchingOrder(orderId, session);
        String paymentStatus = session.path("payment_status").asText("");
        String sessionStatus = session.path("status").asText("");

        if ("paid".equalsIgnoreCase(paymentStatus)) {
            verifySessionAmountAndCurrency(order, session);
            return new ChargeStatusResult(true, false, transactionId,
                    "Stripe confirms the Checkout Session is paid");
        }

        if ("expired".equalsIgnoreCase(sessionStatus)) {
            return new ChargeStatusResult(false, true, transactionId,
                    "Stripe confirms the Checkout Session expired without payment");
        }

        return new ChargeStatusResult(false, false, transactionId,
                "Stripe Checkout Session is not in a definitive paid or expired state");
    }

    @Override
    public RefundResult refund(
            Long orderId,
            BigDecimal amount,
            String transactionId,
            String idempotencyKey) {
        if (transactionId == null || !transactionId.matches("cs_[A-Za-z0-9_]+")) {
            throw new BusinessException("A registered Stripe Checkout session id is required for refund");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("Refund idempotency key is required");
        }

        JsonNode session = loadCheckoutSession(transactionId);
        Order order = requireMatchingOrder(orderId, session);
        verifySessionAmountAndCurrency(order, session);

        if (!"paid".equalsIgnoreCase(session.path("payment_status").asText(""))) {
            throw new BusinessException("Only a paid Stripe Checkout Session can be refunded");
        }

        String paymentIntent = session.path("payment_intent").asText(null);
        if (paymentIntent == null || !paymentIntent.matches("pi_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Stripe Checkout Session has no PaymentIntent for refund");
        }

        long minorAmount = StripeCurrency.toMinorUnits(amount, order.getCurrency());
        if (minorAmount <= 0) {
            throw new BusinessException("Refund amount must be greater than zero");
        }

        String body = "payment_intent=" + encode(paymentIntent)
                + "&amount=" + minorAmount
                + "&reason=requested_by_customer"
                + "&metadata%5Border_id%5D=" + encode(String.valueOf(orderId))
                + "&metadata%5Bcheckout_session_id%5D=" + encode(transactionId);

        try {
            String raw = stripeClient.post()
                    .uri("/v1/refunds")
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode refund = objectMapper.readTree(raw);
            String refundId = refund.path("id").asText(null);
            String status = refund.path("status").asText("");
            if (refundId == null || refundId.isBlank()) {
                throw new IllegalStateException("Stripe refund response is missing an id");
            }
            if ("succeeded".equalsIgnoreCase(status)) {
                return new RefundResult(true, refundId, "Stripe confirms the refund succeeded");
            }
            return new RefundResult(false, refundId,
                    "Stripe refund is not yet successful; current state: " + status);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Stripe rejected the refund request: " + truncate(ex.getResponseBodyAsString()), ex);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Stripe refund outcome could not be confirmed", ex);
        }
    }

    private JsonNode loadCheckoutSession(String sessionId) {
        try {
            String raw = stripeClient.get()
                    .uri("/v1/checkout/sessions/{id}", sessionId)
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(String.class);
            JsonNode session = objectMapper.readTree(raw);
            if (!sessionId.equals(session.path("id").asText(""))) {
                throw new IllegalStateException("Stripe returned a different Checkout Session id");
            }
            return session;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Stripe Checkout Session lookup failed: " + truncate(ex.getResponseBodyAsString()), ex);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Stripe Checkout Session could not be read", ex);
        }
    }

    private Order requireMatchingOrder(Long orderId, JsonNode session) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found for Stripe reconciliation"));
        String metadataOrder = session.path("metadata").path("order_id").asText("");
        String clientReference = session.path("client_reference_id").asText("");
        if (!String.valueOf(orderId).equals(metadataOrder)
                || !String.valueOf(orderId).equals(clientReference)) {
            throw new IllegalArgumentException("Stripe Checkout Session does not match the requested order");
        }
        return order;
    }

    private void verifySessionAmountAndCurrency(Order order, JsonNode session) {
        long sessionAmount = session.path("amount_total").asLong(-1L);
        String sessionCurrency = session.path("currency").asText("");
        long expectedAmount = StripeCurrency.toMinorUnits(order.getTotalAmount(), order.getCurrency());
        if (sessionAmount != expectedAmount
                || !sessionCurrency.equalsIgnoreCase(order.getCurrency())) {
            throw new IllegalArgumentException("Stripe amount or currency does not match the server order");
        }
    }

    private long toMinorUnits(BigDecimal amount, String currency) {
        return StripeCurrency.toMinorUnits(amount, currency);
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Stripe frontend public URL is not configured");
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("https://")) {
            throw new IllegalStateException("Stripe frontend public URL must use HTTPS");
        }
        return normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "unknown Stripe error";
        return value.substring(0, Math.min(900, value.length()));
    }
}