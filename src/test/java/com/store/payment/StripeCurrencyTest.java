package com.store.payment;

import com.store.common.exception.BusinessException;
import com.store.payment.provider.StripeCurrency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeCurrencyTest {

    @Test
    void convertsTwoDecimalCurrencyToMinorUnits() {
        assertEquals(2500L, StripeCurrency.toMinorUnits(new BigDecimal("25.00"), "USD"));
    }

    @Test
    void convertsZeroDecimalCurrencyWithoutMultiplyingByOneHundred() {
        assertEquals(125L, StripeCurrency.toMinorUnits(new BigDecimal("125"), "JPY"));
    }

    @Test
    void convertsThreeDecimalCurrencyToMinorUnits() {
        assertEquals(1234L, StripeCurrency.toMinorUnits(new BigDecimal("1.234"), "BHD"));
    }

    @Test
    void rejectsAmountsThatCannotBeRepresentedInCurrencyMinorUnits() {
        assertThrows(BusinessException.class,
                () -> StripeCurrency.toMinorUnits(new BigDecimal("1.001"), "USD"));
    }

    @Test
    void rejectsMalformedCurrencyCodes() {
        assertThrows(BusinessException.class,
                () -> StripeCurrency.toMinorUnits(new BigDecimal("1.00"), "US"));
    }
}
