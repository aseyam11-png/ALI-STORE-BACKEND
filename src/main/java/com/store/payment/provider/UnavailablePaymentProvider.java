package com.store.payment.provider;

import com.store.payment.entity.PaymentMethod;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fail-closed production fallback until a real online payment gateway is integrated.
 *
 * This bean allows non-payment features (such as registration, authentication, and
 * catalog APIs) to start in the production profile. It never claims that a charge
 * succeeded; online payment attempts are declined. Cash on delivery is handled
 * separately by PaymentService and does not call this provider.
 */
@Component
@Profile("prod")
public class UnavailablePaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        return new PaymentResult(
                false,
                null,
                "Online payments are not configured. Please choose cash on delivery or try again later."
        );
    }
}
