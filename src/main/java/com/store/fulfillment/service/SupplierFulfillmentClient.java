package com.store.fulfillment.service;

import com.store.fulfillment.dto.SupplierFulfillmentRequest;
import com.store.fulfillment.dto.SupplierFulfillmentResponse;
import com.store.supplier.entity.Supplier;

public interface SupplierFulfillmentClient {
    SupplierFulfillmentResponse submit(Supplier supplier, SupplierFulfillmentRequest request);
}
