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
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import com.store.shipping.service.ShipmentService;
import com.store.supplier.entity.SupplierStatus;
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
    private final ShipmentService shipmentService;
    private final PlatformTransactionManager transactionManager;

    public void processTask(Long taskId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Claim claim = tx.execute(status -> claim(taskId));
        if (claim == null) return;

        try {
            SupplierFulfillmentResponse response =
                    supplierFulfillmentClient.submit(claim.shipment().getSupplier(), claim.request());
            tx.executeWithoutResult(status -> complete(taskId, response));
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(status -> fail(taskId, ex));
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + shipment.getOrderId()));

        if (order.getStatus() != OrderStatus.PROCESSING
                || shipment.getStatus() != ShipmentStatus.PENDING
                || shipment.getSupplier() == null
                || shipment.getSupplier().getStatus() != SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(shipment.getSupplier().getFulfillmentEnabled())) {
            cancelTask(task, "Order, shipment, or supplier is no longer eligible for fulfillment");
            return null;
        }

        if (task.getAttempts() >= MAX_ATTEMPTS) {
            permanentlyFail(task, shipment, "Maximum fulfillment attempts reached before a successful supplier response");
            return null;
        }

        List<SupplierFulfillmentRequest.Item> items = order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && item.getSupplier().getId().equals(shipment.getSupplier().getId()))
                .map(this::mapItem)
                .toList();

        if (items.isEmpty()) {
            permanentlyFail(task, shipment, "Shipment has no order items for its supplier");
            return null;
        }

        task.setStatus(FulfillmentTaskStatus.PROCESSING);
        task.setAttempts(task.getAttempts() + 1);
        task.setNextAttemptAt(LocalDateTime.now());
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
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));

        if (task.getStatus() != FulfillmentTaskStatus.PROCESSING) return;

        Shipment shipment = task.getShipment();
        Order order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + shipment.getOrderId()));

        if (order.getStatus() != OrderStatus.PROCESSING || shipment.getStatus() != ShipmentStatus.PENDING) {
            task.setStatus(FulfillmentTaskStatus.CANCELLED);
            task.setLastError("Local order or shipment state changed while supplier request was in flight");
            taskRepository.save(task);
            return;
        }

        shipment.setSupplierOrderId(response.getSupplierOrderId());
        shipment.setCarrier(response.getCarrier());
        shipment.setTrackingNumber(response.getTrackingNumber());
        shipment.setEstimatedDeliveryDate(response.getEstimatedDeliveryDate());
        shipment.setStatus(ShipmentStatus.PROCESSING);
        shipmentRepository.save(shipment);

        order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && item.getSupplier().getId().equals(shipment.getSupplier().getId()))
                .forEach(item -> inventoryService.commitReservedStock(
                        item.getProduct().getId(), item.getQuantity()));

        task.setStatus(FulfillmentTaskStatus.SUCCEEDED);
        task.setLastError(null);
        taskRepository.save(task);
    }

    private void fail(Long taskId, RuntimeException ex) {
        FulfillmentTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment task not found"));

        if (task.getStatus() != FulfillmentTaskStatus.PROCESSING) return;

        task.setLastError(safeMessage(ex));
        if (task.getAttempts() >= MAX_ATTEMPTS) {
            Shipment shipment = task.getShipment();
            permanentlyFail(task, shipment, safeMessage(ex));
        } else {
            task.setStatus(FulfillmentTaskStatus.PENDING);
            task.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(30, task.getAttempts() * 2L)));
            taskRepository.save(task);
        }
    }

    private void permanentlyFail(FulfillmentTask task, Shipment shipment, String reason) {
        task.setStatus(FulfillmentTaskStatus.FAILED);
        task.setLastError(reason);
        taskRepository.save(task);

        if (shipment.getStatus() == ShipmentStatus.PENDING) {
            shipment.setStatus(ShipmentStatus.CANCELLED);
            shipmentRepository.save(shipment);
        }

        releaseSupplierReservations(shipment);
        shipmentService.synchronizeOrderStatus(shipment.getOrderId());
    }

    private void cancelTask(FulfillmentTask task, String reason) {
        task.setStatus(FulfillmentTaskStatus.CANCELLED);
        task.setLastError(reason);
        taskRepository.save(task);
    }

    private void releaseSupplierReservations(Shipment shipment) {
        if (shipment.getSupplier() == null) return;

        Order order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + shipment.getOrderId()));

        order.getItems().stream()
                .filter(item -> item.getSupplier() != null
                        && item.getSupplier().getId().equals(shipment.getSupplier().getId()))
                .forEach(item -> inventoryService.releaseReservedStockUpTo(
                        item.getProduct().getId(), item.getQuantity()));
    }

    private SupplierFulfillmentRequest.Item mapItem(OrderItem item) {
        return SupplierFulfillmentRequest.Item.builder()
                .supplierSku(item.getSupplierSku())
                .quantity(item.getQuantity())
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
