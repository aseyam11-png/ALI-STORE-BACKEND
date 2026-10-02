package com.store.order.repository;

import com.store.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Query("""
            select case when count(o) > 0 then true else false end
            from Order o
            join o.items i
            where o.user.id = :userId
              and i.product.id = :productId
              and o.status = com.store.order.entity.OrderStatus.DELIVERED
            """)
    boolean existsDeliveredOrderForProduct(Long userId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Order o
            where o.status = com.store.order.entity.OrderStatus.PENDING
              and o.reservationExpiresAt <= :now
            order by o.reservationExpiresAt asc
            """)
    List<Order> findTop20ExpiredPendingOrdersForUpdate(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(Long id);
}
