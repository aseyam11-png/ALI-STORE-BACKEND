package com.store.fulfillment.service;

import com.store.fulfillment.entity.FulfillmentTask;
import com.store.fulfillment.entity.FulfillmentTaskStatus;
import com.store.fulfillment.repository.FulfillmentTaskRepository;
import com.store.inventory.service.InventoryService;
import com.store.order.entity.Order;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import com.store.supplier.entity.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private FulfillmentTaskRepository taskRepository;
    @Mock private FulfillmentTaskProcessor taskProcessor;
    @Mock private InventoryService inventoryService;

    private FulfillmentService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentService(
                orderRepository, shipmentRepository, taskRepository, taskProcessor, inventoryService);
    }

    @Test
    void staleProcessingTaskRequiresReconciliationInsteadOfAutomaticReplay() {
        Long taskId = 91L;
        Long orderId = 51L;
        LocalDateTime staleAt = LocalDateTime.now().minusMinutes(20);
        Order order = Order.builder().status(OrderStatus.PROCESSING).build();
        order.setId(orderId);
        Shipment shipment = shipment(orderId);
        FulfillmentTask task = FulfillmentTask.builder()
                .shipment(shipment)
                .status(FulfillmentTaskStatus.PROCESSING)
                .attempts(1)
                .nextAttemptAt(staleAt)
                .build();
        task.setId(taskId);
        task.setUpdatedAt(staleAt);

        when(taskRepository.findStaleProcessingTaskIds(
                eq(FulfillmentTaskStatus.PROCESSING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(taskId));
        when(taskRepository.findOrderIdByTaskId(taskId)).thenReturn(orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.recoverStaleProcessingTasks();

        assertEquals(FulfillmentTaskStatus.RECONCILIATION_REQUIRED, task.getStatus());
        assertFalse(task.getLastError().isBlank());
        verify(taskRepository).save(task);
        verifyNoInteractions(taskProcessor, inventoryService);

        InOrder orderLocking = inOrder(taskRepository, orderRepository);
        orderLocking.verify(taskRepository).findOrderIdByTaskId(taskId);
        orderLocking.verify(orderRepository).findByIdForUpdate(orderId);
        orderLocking.verify(taskRepository).findByIdForUpdate(taskId);
    }

    @Test
    void confirmedSupplierAcceptanceForCancelledOrderIsRecordedForManualReconciliation() {
        Long taskId = 92L;
        Long orderId = 52L;
        Order order = Order.builder().status(OrderStatus.CANCELLED).build();
        order.setId(orderId);
        Shipment shipment = shipment(orderId);
        FulfillmentTask task = FulfillmentTask.builder()
                .shipment(shipment)
                .status(FulfillmentTaskStatus.RECONCILIATION_REQUIRED)
                .attempts(5)
                .build();
        task.setId(taskId);

        when(taskRepository.findOrderIdByTaskId(taskId)).thenReturn(orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.confirmSupplierAccepted(taskId, "SUPPLIER-ORDER-123", "Carrier", "TRACK-123");

        assertEquals("SUPPLIER-ORDER-123", shipment.getSupplierOrderId());
        assertEquals(ShipmentStatus.PROCESSING, shipment.getStatus());
        assertEquals(FulfillmentTaskStatus.RECONCILIATION_REQUIRED, task.getStatus());
        assertFalse(task.getLastError().isBlank());
        verify(shipmentRepository).save(shipment);
        verify(taskRepository).save(task);
        verifyNoInteractions(inventoryService);

        InOrder orderLocking = inOrder(taskRepository, orderRepository);
        orderLocking.verify(taskRepository).findOrderIdByTaskId(taskId);
        orderLocking.verify(orderRepository).findByIdForUpdate(orderId);
        orderLocking.verify(taskRepository).findByIdForUpdate(taskId);
    }

    private Shipment shipment(Long orderId) {
        Supplier supplier = Supplier.builder()
                .name("Test Supplier")
                .externalCode("TEST-SUPPLIER")
                .build();
        supplier.setId(700L);
        Shipment shipment = Shipment.builder()
                .orderId(orderId)
                .supplier(supplier)
                .status(ShipmentStatus.PENDING)
                .build();
        shipment.setId(orderId + 100L);
        return shipment;
    }
}
