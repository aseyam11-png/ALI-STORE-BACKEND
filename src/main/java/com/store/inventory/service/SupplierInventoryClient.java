package com.store.inventory.service;

import com.store.inventory.dto.SupplierInventoryResponse;
import com.store.supplier.entity.Supplier;

public interface SupplierInventoryClient {

    SupplierInventoryResponse fetchInventory(Supplier supplier);
}
