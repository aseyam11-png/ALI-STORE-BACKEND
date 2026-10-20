package com.store.fulfillment.repository;

import com.store.fulfillment.entity.FulfillmentTask;
import com.store.fulfillment.entity.FulfillmentTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FulfillmentTaskRepository extends JpaRepository<FulfillmentTask, Long> {

    List<FulfillmentTask> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            FulfillmentTaskStatus status, LocalDateTime now);

    @Query("""
            select t.id from FulfillmentTask t
            where t.status = :status and t.updatedAt < :cutoff
            order by t.id asc
            """)
    List<Long> findStaleProcessingTaskIds(
            @Param("status") FulfillmentTaskStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Query("select t.shipment.orderId from FulfillmentTask t where t.id = :taskId")
    Long findOrderIdByTaskId(@Param("taskId") Long taskId);

    boolean existsByShipmentId(Long shipmentId);

    List<FulfillmentTask> findTop20ByStatusInOrderByUpdatedAtAsc(List<FulfillmentTaskStatus> statuses);

    long countByStatus(FulfillmentTaskStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FulfillmentTask t
               set t.status = com.store.fulfillment.entity.FulfillmentTaskStatus.CANCELLED,
                   t.lastError = 'Order cancelled before supplier submission'
             where t.shipment.orderId = :orderId
               and t.status = com.store.fulfillment.entity.FulfillmentTaskStatus.PENDING
            """)
    int cancelPendingForOrder(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from FulfillmentTask t join fetch t.shipment s join fetch s.supplier where t.id = :id")
    Optional<FulfillmentTask> findByIdForUpdate(Long id);
}
