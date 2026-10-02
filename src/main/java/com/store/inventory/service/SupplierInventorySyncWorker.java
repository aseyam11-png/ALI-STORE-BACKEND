package com.store.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupplierInventorySyncWorker {

    private final SupplierInventorySyncService syncService;

    @Value("${app.inventory-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.inventory-sync.poll-ms:60000}")
    public void poll() {
        if (!enabled) return;
        try {
            syncService.syncAllActiveSuppliers();
        } catch (RuntimeException ignored) {
            // Supplier failures are isolated so the scheduler remains alive.
        }
    }
}
