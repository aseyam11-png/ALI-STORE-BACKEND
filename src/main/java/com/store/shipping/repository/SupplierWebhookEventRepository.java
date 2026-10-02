package com.store.shipping.repository;

import com.store.shipping.entity.SupplierWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierWebhookEventRepository extends JpaRepository<SupplierWebhookEvent, Long> {
    boolean existsBySupplierCodeAndEventId(String supplierCode, String eventId);
}
