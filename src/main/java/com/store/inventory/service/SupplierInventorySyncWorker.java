package com.store.inventory.service;

import com.store.inventory.dto.SupplierInventoryItem;
import com.store.product.repository.ProductRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SupplierInventorySyncWorker {

    private static final Logger log = LoggerFactory.getLogger(SupplierInventorySyncWorker.class);

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final SupplierInventoryClient inventoryClient;

    @Value("${app.inventory-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.inventory-sync.poll-ms:300000}")
    public void poll() {
        if (!enabled) return;
        supplierRepository.findByStatusAndInventorySyncEnabledTrue(SupplierStatus.ACTIVE)
                .forEach(this::syncSupplier);
    }

    private void syncSupplier(Supplier supplier) {
        try {
            var response = inventoryClient.pull(supplier);
            for (SupplierInventoryItem item : response.getItems()) {
                if (item == null || item.getSupplierSku() == null || item.getSupplierSku().isBlank()
                        || item.getQuantity() == null || item.getQuantity() < 0) {
                    log.warn("Ignoring invalid inventory item from supplier {}", supplier.getExternalCode());
                    continue;
                }
                var product = productRepository.findFirstBySupplierIdAndSupplierSku(
                        supplier.getId(), item.getSupplierSku().trim());
                if (product.isEmpty()) {
                    log.warn("Supplier {} reported unknown SKU {}", supplier.getExternalCode(), item.getSupplierSku());
                    continue;
                }
                LocalDateTime syncedAt = item.getSyncedAt() != null ? item.getSyncedAt() : LocalDateTime.now();
                try {
                    inventoryService.syncSupplierStock(product.get().getId(), item.getQuantity(), syncedAt);
                } catch (RuntimeException ex) {
                    log.error("Failed to sync SKU {} for supplier {}: {}",
                            item.getSupplierSku(), supplier.getExternalCode(), ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            log.error("Supplier inventory synchronization failed for {}: {}",
                    supplier.getExternalCode(), ex.getMessage());
        }
    }
}
