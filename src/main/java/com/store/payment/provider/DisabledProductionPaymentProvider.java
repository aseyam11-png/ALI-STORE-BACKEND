package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fail-closed provider used by the production profile until a real gateway is
 * integrated. It allows the application context to start without pretending
 * that electronic payments are available.
 *
 * <p>PaymentService checks {@link #supports(PaymentMethod)} before reserving a
 * payment attempt. This provider deliberately supports no payment methods;
 * CASH_ON_DELIVERY is handled by PaymentService without invoking a provider.</p>
 *
 * <p>The charge method is a defensive guard for future call sites that might
 * bypass the capability check. It never contacts an external system and never
 * reports a successful charge.</p>
 */
@Component
@Profile("prod")
public class DisabledProductionPaymentProvider implements PaymentProvider {

    @Override
    public boolean supports(PaymentMethod method) {
        return false;
    }

    @Override
    public PaymentResult charge(
            Long orderId,
            BigDecimal amount,
            PaymentMethod method,
            String idempotencyKey) {
        throw new UnsupportedOperationException(
                "Electronic payments are disabled until a production payment gateway is configured");
    }
}
