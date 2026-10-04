package com.store.payment.repository;

import com.store.payment.entity.Refund;
import com.store.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(Long id);

    List<Refund> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);

    List<Refund> findByStatusOrderByCreatedAtAsc(RefundStatus status);

    @Query("""
            select coalesce(sum(r.amount), 0)
            from Refund r
            where r.paymentId = :paymentId
              and r.status = :status
            """)
    BigDecimal sumByPaymentIdAndStatus(
            @Param("paymentId") Long paymentId,
            @Param("status") RefundStatus status);
}
