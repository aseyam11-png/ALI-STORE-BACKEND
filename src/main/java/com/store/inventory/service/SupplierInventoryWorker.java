package com.store.inventory.service;

import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupplierInventoryWorker {

    private static final Logger log = LoggerFactory.getLogger(SupplierInventoryWorker.class);

    private final SupplierInventorySyncService syncService;
    private final SupplierRepository supplierRepository;

    @Value("${app.inventory-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.inventory-sync.poll-ms:300000}")
    public void poll() {
        if (!enabled) {
            return;
        }

        supplierRepository.findByStatus(SupplierStatus.ACTIVE).stream()
                .filter(supplier -> Boolean.TRUE.equals(supplier.getInventorySyncEnabled()))
                .forEach(supplier -> {
                    try {
                        syncService.syncSupplier(supplier.getId());
                    } catch (RuntimeException ex) {
                        // One broken supplier must not stop synchronization for all other suppliers.
                        log.warn("Supplier inventory sync failed for supplier {}: {}",
                                supplier.getExternalCode(), ex.getMessage());
                    }
                });
    }
}
