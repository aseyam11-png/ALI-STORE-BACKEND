package com.store.inventory.repository;

import com.store.inventory.entity.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from InventoryReservation r
             where r.orderId = :orderId and r.productId = :productId
            """)
    Optional<InventoryReservation> findByOrderIdAndProductIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("productId") Long productId);

    Optional<InventoryReservation> findByOrderIdAndProductId(Long orderId, Long productId);
}
