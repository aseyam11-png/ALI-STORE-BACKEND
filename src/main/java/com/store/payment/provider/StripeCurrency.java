package com.store.payment.provider;

import com.store.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

public final class StripeCurrency {

    private static final Set<String> ZERO_DECIMAL = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");
    private static final Set<String> THREE_DECIMAL = Set.of(
            "BHD", "JOD", "KWD", "OMR", "TND");

    private StripeCurrency() {
    }

    public static int exponent(String currency) {
        String normalized = normalize(currency);
        if (ZERO_DECIMAL.contains(normalized)) return 0;
        if (THREE_DECIMAL.contains(normalized)) return 3;
        return 2;
    }

    public static long toMinorUnits(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("Payment amount cannot be negative or null");
        }
        int scale = exponent(currency);
        try {
            return amount.movePointRight(scale)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessException(
                    "Payment amount cannot be represented in Stripe minor units for " + normalize(currency));
        }
    }

    public static String normalize(String currency) {
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new BusinessException("Payment currency must be a 3-letter ISO code");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}