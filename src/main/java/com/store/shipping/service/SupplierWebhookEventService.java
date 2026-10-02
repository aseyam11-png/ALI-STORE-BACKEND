package com.store.shipping.service;

import com.store.shipping.repository.SupplierWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierWebhookEventService {

    private final SupplierWebhookEventRepository repository;

    @Transactional
    public boolean recordIfNew(String supplierCode, String eventId) {
        return repository.insertIfAbsent(supplierCode, eventId) == 1;
    }
}
