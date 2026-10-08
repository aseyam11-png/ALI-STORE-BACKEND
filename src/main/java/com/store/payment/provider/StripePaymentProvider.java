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
import java.util.Set;

@Component
@Profile("prod")
public class StripePaymentProvider implements PaymentProvider {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF","CLP","DJF","GNF","JPY","KMF","KRW","MGA","PYG","RWF","UGX",
            "VND","VUV","XAF","XOF","XPF");

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

        long minorAmount = toMinorUnits(amount, order.getCurrency());
        String baseUrl = normalizeBaseUrl(frontendPublicUrl);
        String successUrl = baseUrl + "/buyer/orders/" + orderId
                + "/confirmation?payment=success&session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = baseUrl + "/shop/checkout?payment=cancelled&orderId=" + orderId;

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

    private long toMinorUnits(BigDecimal amount, String currency) {
        int scale = ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase()) ? 0 : 2;
        try {
            return amount.movePointRight(scale)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessException("Payment amount cannot be represented in the minor units of " + currency);
        }
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