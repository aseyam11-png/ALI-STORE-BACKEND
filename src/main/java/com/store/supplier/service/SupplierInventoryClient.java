package com.store.supplier.service;

import com.store.supplier.entity.Supplier;

public interface SupplierInventoryClient {
    InventorySnapshot fetch(Supplier supplier, String supplierSku);

    record InventorySnapshot(int quantity, java.math.BigDecimal unitCost, String currency) {}
}