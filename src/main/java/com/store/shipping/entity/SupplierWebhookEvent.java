package com.store.shipping.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_supplier_webhook_event", columnNames = {"supplier_code", "event_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", nullable = false, length = 80)
    private String supplierCode;

    @Column(name = "event_id", nullable = false, length = 150)
    private String eventId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
