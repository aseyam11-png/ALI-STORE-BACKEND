package com.store.product.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreProductCodeGeneratorTest {

    @Test
    void generatesStablePaddedCodeFromDatabaseId() {
        assertEquals("ALI-00000042", StoreProductCodeGenerator.forProductId(42L));
        assertEquals("ALI-12345678", StoreProductCodeGenerator.forProductId(12345678L));
    }

    @Test
    void rejectsMissingOrUnpersistedProductIds() {
        assertThrows(IllegalArgumentException.class, () -> StoreProductCodeGenerator.forProductId(null));
        assertThrows(IllegalArgumentException.class, () -> StoreProductCodeGenerator.forProductId(0L));
        assertThrows(IllegalArgumentException.class, () -> StoreProductCodeGenerator.forProductId(-1L));
    }
}
