package com.store.supplier.service;

import com.store.common.exception.BusinessException;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierReadinessService {

    private final SupplierRepository supplierRepository;
    private final SupplierCredentialResolver credentialResolver;

    @Value("${app.fulfillment.enabled:false}")
    private boolean fulfillmentEnabled;

    public void assertReady(Long supplierId) {
        if (supplierId == null) {
            throw new BusinessException("Product has no configured supplier for fulfillment");
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new BusinessException("Configured supplier was not found: " + supplierId));
        if (!fulfillmentEnabled) {
            throw new BusinessException("Checkout is unavailable while global supplier fulfillment is disabled");
        }
        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new BusinessException("Supplier is not active for fulfillment: " + supplier.getExternalCode());
        }
        if (!Boolean.TRUE.equals(supplier.getFulfillmentEnabled())) {
            throw new BusinessException("Supplier fulfillment is disabled: " + supplier.getExternalCode());
        }
        if (supplier.getApiBaseUrl() == null || supplier.getApiBaseUrl().isBlank()) {
            throw new BusinessException("Supplier fulfillment API is not configured: " + supplier.getExternalCode());
        }
        if (credentialResolver.resolveFulfillmentToken(supplier).isBlank()) {
            throw new BusinessException("Supplier fulfillment credentials are not configured: " + supplier.getExternalCode());
        }
    }
}
