package com.store.fulfillment.service;

import com.store.common.exception.ResourceNotFoundException;
import com.store.fulfillment.entity.FulfillmentTask;
import com.store.fulfillment.entity.FulfillmentTaskStatus;
import com.store.fulfillment.repository.FulfillmentTaskRepository;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import com.store.supplier.entity.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final FulfillmentTaskRepository taskRepository;
    private final FulfillmentTaskProcessor taskProcessor;

    public FulfillmentTask findTaskByShipmentId(Long shipmentId) {
        return taskRepository.findByShipmentId(shipmentId).orElse(null);
    }

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

            FulfillmentTask existing = taskRepository.findByShipmentId(shipment.getId()).orElse(null);
            if (existing == null) {
                taskRepository.save(FulfillmentTask.builder()
                        .shipment(shipment)
                        .status(FulfillmentTaskStatus.PENDING)
                        .attempts(0)
                        .nextAttemptAt(LocalDateTime.now())
                        .build());
            } else if (existing.getStatus() == FulfillmentTaskStatus.CANCELLED
                    && shipment.getStatus() == ShipmentStatus.PENDING) {
                existing.setStatus(FulfillmentTaskStatus.PENDING);
                existing.setAttempts(0);
                existing.setNextAttemptAt(LocalDateTime.now());
                existing.setLastError(null);
                taskRepository.save(existing);
            }
        }
    }

    @Transactional
    public void cancelPendingWorkForOrder(Long orderId) {
        List<Shipment> shipments = shipmentRepository.findByOrderIdOrderByIdAsc(orderId);
        for (Shipment shipment : shipments) {
            FulfillmentTask task = taskRepository.findByShipmentIdForUpdate(shipment.getId()).orElse(null);
            if (task != null && task.getStatus() == FulfillmentTaskStatus.PENDING) {
                task.setStatus(FulfillmentTaskStatus.CANCELLED);
                task.setLastError("Order cancelled before supplier fulfillment started");
                taskRepository.save(task);
            }
            if (shipment.getStatus() == ShipmentStatus.PENDING) {
                shipment.setStatus(ShipmentStatus.CANCELLED);
                shipmentRepository.save(shipment);
            }
        }
    }

    @Transactional
    public void recoverStaleProcessingTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<FulfillmentTask> tasks = taskRepository
                .findTop20ByStatusAndUpdatedAtBeforeOrderByIdAsc(FulfillmentTaskStatus.PROCESSING, cutoff);

        tasks.stream()
                .filter(task -> task.getShipment().getSupplierOrderId() == null)
                .forEach(task -> {
                    task.setStatus(FulfillmentTaskStatus.PENDING);
                    task.setNextAttemptAt(LocalDateTime.now());
                    taskRepository.save(task);
                });
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
