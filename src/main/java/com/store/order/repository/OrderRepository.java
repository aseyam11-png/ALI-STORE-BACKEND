package com.store.order.repository;

import com.store.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.status = com.store.order.entity.OrderStatus.PENDING
              and o.reservationExpiresAt <= :now
            order by o.reservationExpiresAt asc
            """)
    java.util.List<Order> findTop20ExpiredPendingOrdersForUpdate(java.time.LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    java.util.Optional<Order> findByIdForUpdate(Long id);
}