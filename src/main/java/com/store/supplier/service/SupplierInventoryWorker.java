package com.store.supplier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupplierInventoryWorker {
    private final SupplierInventorySyncService syncService;

    @Value("${app.supplier-sync.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.supplier-sync.poll-ms:60000}")
    public void poll() {
        if (enabled) {
            syncService.syncAll();
        }
    }
}