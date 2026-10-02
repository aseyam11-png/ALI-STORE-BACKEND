package com.store.inventory.service;

import com.store.product.entity.Product;
import com.store.supplier.entity.Supplier;

public interface SupplierInventoryClient {
    SupplierInventoryResult fetch(Supplier supplier, Product product);

    record SupplierInventoryResult(
            int quantity,
            java.math.BigDecimal supplierCost,
            String currency,
            java.time.LocalDateTime syncedAt
    ) {}
}
