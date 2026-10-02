package com.store.fulfillment.repository;

import com.store.fulfillment.entity.FulfillmentTask;
import com.store.fulfillment.entity.FulfillmentTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FulfillmentTaskRepository extends JpaRepository<FulfillmentTask, Long> {

    List<FulfillmentTask> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            FulfillmentTaskStatus status, LocalDateTime now);

    List<FulfillmentTask> findTop20ByStatusAndUpdatedAtBeforeOrderByIdAsc(
            FulfillmentTaskStatus status, LocalDateTime cutoff);

    boolean existsByShipmentId(Long shipmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from FulfillmentTask t join fetch t.shipment s join fetch s.supplier where t.id = :id")
    Optional<FulfillmentTask> findByIdForUpdate(Long id);
}
