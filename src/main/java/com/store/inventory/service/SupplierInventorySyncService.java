package com.store.inventory.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.SupplierInventoryResponse;
import com.store.inventory.dto.SupplierInventorySyncReport;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierInventorySyncService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final SupplierInventoryClient supplierInventoryClient;
    private final com.store.pricing.service.PricingService pricingService;
    private final PlatformTransactionManager transactionManager;

    public SupplierInventorySyncReport syncSupplier(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + supplierId));

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new BusinessException("Supplier is not active");
        }
        if (!Boolean.TRUE.equals(supplier.getInventorySyncEnabled())) {
            throw new BusinessException("Supplier inventory synchronization is disabled");
        }

        // The external call is outside the DB transaction. A slow supplier must
        // never keep inventory/product locks open while waiting on the network.
        SupplierInventoryResponse payload = supplierInventoryClient.fetchInventory(supplier);
        LocalDateTime syncedAt = LocalDateTime.now();

        return applyPayload(supplierId, payload, syncedAt);
    }

    private SupplierInventorySyncReport applyPayload(
            Long supplierId,
            SupplierInventoryResponse payload,
            LocalDateTime syncedAt) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + supplierId));

        List<String> errors = new ArrayList<>();
        int received = payload.getItems() == null ? 0 : payload.getItems().size();
        int matched = 0;
        int updated = 0;
        int rejected = 0;

        if (payload.getItems() != null) {
            for (SupplierInventoryResponse.Item item : payload.getItems()) {
                if (item == null) {
                    rejected++;
                    errors.add("Rejected null inventory item");
                    continue;
                }
                String supplierProductId = normalizeOptional(item.getSupplierProductId());
                String supplierSku = normalizeOptional(item.getSupplierSku());
                if (supplierProductId == null && supplierSku == null) {
                    rejected++;
                    errors.add("Rejected inventory item without supplierProductId or supplierSku");
                    continue;
                }
                String itemKey = supplierProductId != null ? supplierProductId : supplierSku;
                if (item.getQuantity() == null || item.getQuantity() < 0) {
                    rejected++;
                    errors.add("Rejected " + itemKey + ": quantity must be >= 0");
                    continue;
                }

                try {
                    ApplyResult result = new TransactionTemplate(transactionManager).execute(
                            status -> applySku(supplierId, item, syncedAt));
                    if (result == null || !result.matched()) continue;
                    matched++;
                    if (result.updated()) updated++;
                    if (result.error() != null) {
                        rejected++;
                        errors.add("Rejected " + itemKey + ": " + result.error());
                    }
                } catch (RuntimeException ex) {
                    // Each SKU has its own transaction. One malformed product/cost must
                    // not roll back updates already committed for other SKUs in this payload.
                    rejected++;
                    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    errors.add("Rejected " + itemKey + ": " + message.substring(0, Math.min(200, message.length())));
                }
            }
        }

        return SupplierInventorySyncReport.builder()
                .supplierId(supplier.getId())
                .supplierCode(supplier.getExternalCode())
                .receivedItems(received)
                .matchedProducts(matched)
                .updatedProducts(updated)
                .rejectedItems(rejected)
                .syncedAt(syncedAt)
                .errors(List.copyOf(errors))
                .build();
    }

    private ApplyResult applySku(Long supplierId, SupplierInventoryResponse.Item item, LocalDateTime syncedAt) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + supplierId));
        if (supplier.getStatus() != SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(supplier.getInventorySyncEnabled())) {
            throw new BusinessException("Supplier is not active for inventory synchronization");
        }

        String supplierSku = normalizeOptional(item.getSupplierSku());
        String supplierProductId = normalizeOptional(item.getSupplierProductId());
        Product product = null;
        if (supplierProductId != null) {
            product = productRepository.findBySupplierIdAndSupplierProductId(supplier.getId(), supplierProductId).orElse(null);
            if (product == null && supplierSku != null) {
                Product skuMatch = productRepository.findBySupplierIdAndSupplierSku(supplier.getId(), supplierSku).orElse(null);
                if (skuMatch != null && normalizeOptional(skuMatch.getSupplierProductId()) == null) {
                    // Adopt a stable supplier ID for a product already matched by its legacy supplier SKU.
                    skuMatch.setSupplierProductId(supplierProductId);
                    product = productRepository.save(skuMatch);
                } else if (skuMatch != null && !supplierProductId.equals(skuMatch.getSupplierProductId())) {
                    return new ApplyResult(true, false, "Supplier SKU is already mapped to a different supplier product ID");
                }
            }
        } else if (supplierSku != null) {
            product = productRepository.findBySupplierIdAndSupplierSku(supplier.getId(), supplierSku).orElse(null);
        }
        if (product == null) return new ApplyResult(false, false, null);

        // Validate the complete cost contract before mutating inventory. This keeps a
        // malformed price/currency record from partially updating the same SKU.
        if (item.getUnitCost() != null) {
            validateSupplierCost(product, item.getUnitCost(), item.getCurrency());
        }

        LocalDateTime observedAt = item.getObservedAt() != null ? item.getObservedAt() : syncedAt;
        InventoryResponse response = inventoryService.syncSupplierStock(
                product.getId(), item.getQuantity(), observedAt);

        // A stale response must not overwrite stock or supplier cost.
        if (!observedAt.equals(response.getLastSyncedAt())) {
            return new ApplyResult(true, false, "Ignored an out-of-order supplier snapshot");
        }

        if (item.getUnitCost() != null) {
            updateSupplierCost(product, item.getUnitCost(), item.getCurrency());
            if (product.getStatus() == ProductStatus.ACTIVE) {
                pricingService.repriceProduct(product);
                if (product.getPrice().compareTo(product.getSupplierCost()) < 0) {
                    product.setStatus(ProductStatus.DRAFT);
                }
            }
            productRepository.save(product);
        }
        return new ApplyResult(true, true, null);
    }

    private record ApplyResult(boolean matched, boolean updated, String error) {
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updateSupplierCost(Product product, BigDecimal unitCost, String currency) {
        validateSupplierCost(product, unitCost, currency);
        product.setSupplierCost(unitCost);
    }

    private void validateSupplierCost(Product product, BigDecimal unitCost, String currency) {
        if (unitCost.signum() <= 0) {
            throw new BusinessException("Supplier cost must be greater than zero for " + product.getSku());
        }

        String normalizedCurrency = normalizeOptional(currency);
        if (normalizedCurrency == null) {
            throw new BusinessException("Supplier cost currency is required for SKU " + product.getSupplierSku());
        }

        String productCurrency = normalizeOptional(product.getCurrency());
        if (productCurrency == null
                || !normalizedCurrency.equalsIgnoreCase(productCurrency)) {
            throw new BusinessException("Currency mismatch for supplier SKU " + product.getSupplierSku()
                    + ": supplier=" + normalizedCurrency + ", product=" + productCurrency);
        }
    }
}
