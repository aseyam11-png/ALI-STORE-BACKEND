package com.store.shipping.repository;

import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findByOrderIdOrderByIdAsc(Long orderId);

    boolean existsByOrderIdAndSupplierId(Long orderId, Long supplierId);

    Optional<Shipment> findByOrderIdAndSupplierId(Long orderId, Long supplierId);

    Optional<Shipment> findBySupplierOrderId(String supplierOrderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s join fetch s.supplier where s.supplierOrderId = :supplierOrderId")
    Optional<Shipment> findBySupplierOrderIdForUpdate(String supplierOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s join fetch s.supplier where s.id = :id")
    Optional<Shipment> findByIdForUpdate(Long id);

    List<Shipment> findByStatus(ShipmentStatus status);
}
