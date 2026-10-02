package com.store.shipping.service;

import com.store.shipping.entity.SupplierWebhookEvent;
import com.store.shipping.repository.SupplierWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SupplierWebhookEventService {
    private final SupplierWebhookEventRepository repository;

    @Transactional
    public boolean recordIfNew(String supplierCode, String eventId) {
        if (repository.existsBySupplierCodeAndEventId(supplierCode, eventId)) return false;
        repository.saveAndFlush(SupplierWebhookEvent.builder()
                .supplierCode(supplierCode)
                .eventId(eventId)
                .receivedAt(LocalDateTime.now())
                .build());
        return true;
    }
}
