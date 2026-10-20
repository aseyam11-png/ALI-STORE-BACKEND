package com.store.fulfillment.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.fulfillment.dto.SupplierFulfillmentRequest;
import com.store.fulfillment.dto.SupplierFulfillmentResponse;
import com.store.fulfillment.entity.FulfillmentTask;
import com.store.fulfillment.entity.FulfillmentTaskStatus;
import com.store.fulfillment.repository.FulfillmentTaskRepository;
import com.store.inventory.service.InventoryService;
import com.store.order.entity.Order;
import com.store.order.entity.OrderItem;
import com.store.order.repository.OrderRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FulfillmentTaskProcessor {

    private static final int MAX_ATTEMPTS = 5;

    private final FulfillmentTaskRepository taskRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final InventoryService inventoryService;
    private final SupplierFulfillmentClient supplierFulfillmentClient;
    private final PlatformTransactionManager transactionManager;

    public void processTask(Long taskId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Claim claim = tx.execute(status -> claim(taskId));
        if (claim == null) {
            return;
        }

        SupplierFulfillmentResponse response;
        try {
            response = supplierFulfillmentClient.submit(
                    claim.shipment().getSupplier(), claim.request());
        } catch (RuntimeException supplierFailure) {
            // Only failures from the supplier submission enter the retry policy.
            tx.executeWithoutResult(status -> fail(taskId, supplierFailure));
            return;
        }

        try {
            tx.executeWithoutResult(status -> complete(taskId, response));
        } catch (RuntimeException localPersistenceFailure) {
            // The supplier has already returned success. Never blindly submit the same
            // order again because the local transaction failed; manual reconciliation
            // must inspect the supplier acceptance and local state.
            tx.executeWithoutResult(status ->
                    markReconciliationRequired(taskId, response, localPersistenceFailure));
        }
    }

    private Claim claim(Long taskId) {
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));

        if (task.getStatus() != FulfillmentTaskStatus.PENDING
                || task.getNextAttemptAt().isAfter(LocalDateTime.now())) {
            return null;
        }

        Shipment shipment = task.getShipment();
        Order order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + shipment.getOrderId()));

        if (order.getStatus() != com.store.order.entity.OrderStatus.PROCESSING) {
            task.setStatus(order.getStatus() == com.store.order.entity.OrderStatus.CANCELLED
                    ? FulfillmentTaskStatus.CANCELLED : FulfillmentTaskStatus.RECONCILIATION_REQUIRED);
            task.setLastError("Order is not in PROCESSING state; supplier submission was not started");
            taskRepository.save(task);
            return null;
        }

        List<SupplierFulfillmentRequest.Item> items = order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && item.getSupplier().getId().equals(shipment.getSupplier().getId()))
                .map(this::mapItem)
                .toList();

        if (items.isEmpty()) {
            throw new BusinessException("Shipment has no order items for its supplier");
        }

        task.setStatus(FulfillmentTaskStatus.PROCESSING);
        task.setAttempts(task.getAttempts() + 1);
        taskRepository.save(task);

        SupplierFulfillmentRequest request = SupplierFulfillmentRequest.builder()
                .orderId(order.getId())
                .shipmentId(shipment.getId())
                .idempotencyKey("ALI-STORE:" + order.getId() + ":" + shipment.getId())
                .supplierCode(shipment.getSupplier().getExternalCode())
                .shippingAddress(order.getShippingAddress())
                .items(items)
                .build();

        return new Claim(shipment, request);
    }

    private void complete(Long taskId, SupplierFulfillmentResponse response) {
        // Lock in the same order used by order cancellation (order, then task) to
        // avoid a task/order deadlock while a supplier request is in flight.
        FulfillmentTask observed = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));
        Shipment shipment = observed.getShipment();
        Order order = orderRepository.findByIdForUpdate(shipment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + shipment.getOrderId()));
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));

        if (task.getStatus() == FulfillmentTaskStatus.SUCCEEDED) {
            // Idempotent duplicate completion: never regress a successful task.
            return;
        }

        if (task.getStatus() != FulfillmentTaskStatus.PROCESSING
                || order.getStatus() != com.store.order.entity.OrderStatus.PROCESSING) {
            // The external supplier has already accepted the request even if cancellation
            // raced with the response. Persist the supplier's identifiers for operators;
            // never treat this as success or retry it blindly.
            FulfillmentTaskStatus previousTaskStatus = task.getStatus();
            persistSupplierAcceptance(shipment, response);
            task.setStatus(FulfillmentTaskStatus.RECONCILIATION_REQUIRED);
            task.setLastError("Supplier accepted the order after local task/order state changed"
                    + " (previousTask=" + previousTaskStatus + ", order=" + order.getStatus()
                    + "); supplier cancellation and inventory/financial reconciliation are required");
            taskRepository.save(task);
            return;
        }

        persistSupplierAcceptance(shipment, response);

        order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && item.getSupplier().getId().equals(shipment.getSupplier().getId()))
                .forEach(item -> inventoryService.commitReservedStock(item.getId()));

        task.setStatus(FulfillmentTaskStatus.SUCCEEDED);
        task.setLastError(null);
        taskRepository.save(task);
    }

    private void persistSupplierAcceptance(Shipment shipment, SupplierFulfillmentResponse response) {
        shipment.setSupplierOrderId(response.getSupplierOrderId());
        shipment.setCarrier(response.getCarrier());
        shipment.setTrackingNumber(response.getTrackingNumber());
        shipment.setEstimatedDeliveryDate(response.getEstimatedDeliveryDate());
        shipment.setStatus(ShipmentStatus.PROCESSING);
        shipmentRepository.save(shipment);
    }

    private void fail(Long taskId, RuntimeException ex) {
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));

        if (task.getStatus() != FulfillmentTaskStatus.PROCESSING) {
            return;
        }

        task.setLastError(safeMessage(ex));
        if (task.getAttempts() >= MAX_ATTEMPTS) {
            task.setStatus(FulfillmentTaskStatus.FAILED);
        } else {
            task.setStatus(FulfillmentTaskStatus.PENDING);
            task.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(30, task.getAttempts() * 2L)));
        }
        taskRepository.save(task);
    }

    private void markReconciliationRequired(
            Long taskId, SupplierFulfillmentResponse response, RuntimeException ex) {
        // Apply the same order -> task lock order as completion/cancellation.
        FulfillmentTask observed = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));
        Shipment shipment = observed.getShipment();
        orderRepository.findByIdForUpdate(shipment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + shipment.getOrderId()));
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));
        if (task.getStatus() == FulfillmentTaskStatus.SUCCEEDED) return;

        // The prior local transaction may have rolled back entirely, and a concurrent
        // cancellation may have changed the task status. Preserve the supplier acceptance
        // response instead of silently leaving a cancelled/pending row with no supplier ID.
        persistSupplierAcceptance(shipment, response);
        task.setStatus(FulfillmentTaskStatus.RECONCILIATION_REQUIRED);
        String supplierOrderId = response.getSupplierOrderId();
        task.setLastError("Supplier submission returned successfully"
                + (supplierOrderId == null || supplierOrderId.isBlank()
                        ? "" : " with supplier order id " + supplierOrderId)
                + " but local persistence failed: " + safeMessage(ex));
        taskRepository.save(task);
    }

    private SupplierFulfillmentRequest.Item mapItem(OrderItem item) {
        return SupplierFulfillmentRequest.Item.builder()
                .supplierSku(item.getSupplierSku())
                .quantity(item.getQuantity())
                .supplierUnitCost(item.getSupplierUnitCost())
                .build();
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 1000));
    }

    private record Claim(Shipment shipment, SupplierFulfillmentRequest request) {
    }
}
