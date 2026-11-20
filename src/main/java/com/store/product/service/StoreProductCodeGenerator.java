package com.store.product.service;

import java.util.Locale;

/** Creates a stable human-readable ALI STORE code from the database product ID. */
public final class StoreProductCodeGenerator {

    private StoreProductCodeGenerator() {
    }

    public static String forProductId(Long productId) {
        if (productId == null || productId < 1) {
            throw new IllegalArgumentException("A persisted positive product ID is required");
        }
        return "ALI-" + String.format(Locale.ROOT, "%08d", productId);
    }
}
