package com.store.shipping.repository;

import com.store.shipping.entity.SupplierWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierWebhookEventRepository extends JpaRepository<SupplierWebhookEvent, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO supplier_webhook_events (supplier_code, event_id, received_at)
            VALUES (:supplierCode, :eventId, CURRENT_TIMESTAMP)
            ON CONFLICT (supplier_code, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(String supplierCode, String eventId);
}
