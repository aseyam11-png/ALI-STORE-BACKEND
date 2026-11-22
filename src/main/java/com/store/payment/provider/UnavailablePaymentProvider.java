package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fail-closed production fallback until a real online payment gateway is integrated.
 * Cash on delivery is handled separately by PaymentService.
 */
@Component
@Profile("prod")
public class UnavailablePaymentProvider implements PaymentProvider {

    @Override
    public boolean supports(PaymentMethod method) {
        return false;
    }

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        throw new UnsupportedOperationException(
                "Electronic payments are disabled until a production gateway is configured");
    }
}
