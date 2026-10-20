package com.store.fulfillment.service;

import com.store.common.exception.ResourceNotFoundException;
import com.store.fulfillment.entity.FulfillmentTask;
import com.store.fulfillment.entity.FulfillmentTaskStatus;
import com.store.fulfillment.dto.FulfillmentTaskAttentionResponse;
import com.store.common.exception.BusinessException;
import com.store.fulfillment.repository.FulfillmentTaskRepository;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import com.store.supplier.entity.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final FulfillmentTaskRepository taskRepository;
    private final FulfillmentTaskProcessor taskProcessor;

    /**
     * Creates durable per-supplier fulfillment work. The actual supplier HTTP
     * request is intentionally performed by FulfillmentTaskProcessor outside
     * this transaction.
     */
    @Transactional
    public void enqueueForOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        var bySupplier = order.getItems().stream()
                .filter(item -> item.getSupplier() != null)
                .collect(Collectors.groupingBy(item -> item.getSupplier().getId()));

        for (var entry : bySupplier.entrySet()) {
            Supplier supplier = entry.getValue().get(0).getSupplier();

            Shipment shipment = shipmentRepository.findByOrderIdAndSupplierId(orderId, supplier.getId())
                    .orElseGet(() -> shipmentRepository.save(
                            Shipment.builder()
                                    .orderId(orderId)
                                    .supplier(supplier)
                                    .shippingAddressSnapshot(order.getShippingAddress())
                                    .status(ShipmentStatus.PENDING)
                                    .build()));

            if (!taskRepository.existsByShipmentId(shipment.getId())) {
                taskRepository.save(FulfillmentTask.builder()
                        .shipment(shipment)
                        .status(FulfillmentTaskStatus.PENDING)
                        .attempts(0)
                        .nextAttemptAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    @Transactional
    public void cancelPendingTasksForOrder(Long orderId) {
        taskRepository.cancelPendingForOrder(orderId);
    }

    @Transactional
    public void recoverStaleProcessingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Long> taskIds = taskRepository.findStaleProcessingTaskIds(
                FulfillmentTaskStatus.PROCESSING, cutoff, org.springframework.data.domain.PageRequest.of(0, 20));

        for (Long taskId : taskIds) {
            // Query scalar IDs first so no task row is locked before the order row.
            Long orderId = taskRepository.findOrderIdByTaskId(taskId);
            if (orderId == null) continue;

            if (orderRepository.findByIdForUpdate(orderId).isEmpty()) continue;
            FulfillmentTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
            if (task == null
                    || task.getStatus() != FulfillmentTaskStatus.PROCESSING
                    || task.getUpdatedAt() == null
                    || !task.getUpdatedAt().isBefore(cutoff)) {
                continue;
            }

            // The supplier may have accepted the request before this worker stopped.
            // Do not automatically replay an external order with an unknown outcome.
            task.setStatus(FulfillmentTaskStatus.RECONCILIATION_REQUIRED);
            task.setLastError("Fulfillment worker stopped while supplier outcome may be unknown; "
                    + "verify supplier acceptance before retrying");
            taskRepository.save(task);
            log.warn("Fulfillment task {} requires reconciliation after stale PROCESSING state; order={}",
                    task.getId(), orderId);
        }
    }

    @Transactional(readOnly = true)
    public void logOperationalAlerts() {
        List<FulfillmentTaskAttentionResponse> alerts = getTasksNeedingAttention();
        if (!alerts.isEmpty()) {
            log.warn("Fulfillment tasks require manual attention (up to 20 shown): {}", alerts);
        }
    }

    @Transactional(readOnly = true)
    public List<FulfillmentTaskAttentionResponse> getTasksNeedingAttention() {
        return taskRepository.findTop20ByStatusInOrderByUpdatedAtAsc(List.of(
                        FulfillmentTaskStatus.FAILED, FulfillmentTaskStatus.RECONCILIATION_REQUIRED))
                .stream()
                .map(task -> {
                    Shipment shipment = task.getShipment();
                    return new FulfillmentTaskAttentionResponse(
                            task.getId(), task.getStatus(), task.getAttempts(),
                            shipment.getOrderId(), shipment.getId(),
                            shipment.getSupplier().getExternalCode(), task.getLastError(), task.getUpdatedAt());
                })
                .toList();
    }

    @Transactional
    public void requeueAfterManualConfirmation(Long taskId, String confirmation, String operatorNote) {
        if (!"SUPPLIER_CONFIRMED_NOT_ACCEPTED".equals(confirmation)) {
            throw new BusinessException("Manual retry requires explicit supplier confirmation that no order was accepted");
        }
        Long orderId = taskRepository.findOrderIdByTaskId(taskId);
        if (orderId == null) throw new ResourceNotFoundException("Fulfillment task not found");
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));
        if (task.getStatus() != FulfillmentTaskStatus.FAILED
                && task.getStatus() != FulfillmentTaskStatus.RECONCILIATION_REQUIRED) {
            throw new BusinessException("Only failed or reconciliation-required tasks can be manually requeued");
        }
        Shipment shipment = task.getShipment();
        if (shipment.getSupplierOrderId() != null && !shipment.getSupplierOrderId().isBlank()) {
            throw new BusinessException("Supplier order id already exists; task cannot be requeued");
        }
        if (order.getStatus() != com.store.order.entity.OrderStatus.PROCESSING) {
            throw new BusinessException("Only processing orders can be requeued");
        }
        task.setStatus(FulfillmentTaskStatus.PENDING);
        task.setAttempts(0);
        task.setNextAttemptAt(LocalDateTime.now());
        task.setLastError("Manual retry approved after supplier confirmed no accepted order. "
                + (operatorNote == null ? "" : operatorNote.trim()));
        taskRepository.save(task);
    }

    @Transactional
    public void confirmSupplierAccepted(Long taskId, String supplierOrderId, String carrier, String trackingNumber) {
        if (supplierOrderId == null || supplierOrderId.isBlank()) {
            throw new BusinessException("Supplier order id is required for reconciliation");
        }
        Long orderId = taskRepository.findOrderIdByTaskId(taskId);
        if (orderId == null) throw new ResourceNotFoundException("Fulfillment task not found");
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));
        if (task.getStatus() != FulfillmentTaskStatus.FAILED
                && task.getStatus() != FulfillmentTaskStatus.RECONCILIATION_REQUIRED) {
            throw new BusinessException("Task is not awaiting reconciliation");
        }
        Shipment shipment = task.getShipment();
        if (order.getStatus() != com.store.order.entity.OrderStatus.PROCESSING) {
            throw new BusinessException("Cancelled or non-processing orders require separate supplier and financial reconciliation");
        }

        shipment.setSupplierOrderId(supplierOrderId.trim());
        shipment.setCarrier(carrier == null ? null : carrier.trim());
        shipment.setTrackingNumber(trackingNumber == null ? null : trackingNumber.trim());
        shipment.setStatus(ShipmentStatus.PROCESSING);
        shipmentRepository.save(shipment);

        order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && item.getSupplier().getId().equals(shipment.getSupplier().getId()))
                .forEach(item -> inventoryService.commitReservedStock(item.getId()));

        task.setStatus(FulfillmentTaskStatus.SUCCEEDED);
        task.setLastError(null);
        taskRepository.save(task);
    }

    public void processDueTasks() {
        List<Long> taskIds = taskRepository
                .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        FulfillmentTaskStatus.PENDING, LocalDateTime.now())
                .stream()
                .map(FulfillmentTask::getId)
                .toList();

        taskIds.forEach(taskProcessor::processTask);
    }
}
