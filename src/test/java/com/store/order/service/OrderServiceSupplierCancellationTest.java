package com.store.order.service;

import com.store.cart.repository.CartItemRepository;
import com.store.cart.repository.CartRepository;
import com.store.coupon.service.CouponService;
import com.store.fulfillment.service.FulfillmentService;
import com.store.inventory.service.InventoryService;
import com.store.order.entity.Order;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.repository.PaymentRepository;
import com.store.payment.service.RefundService;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceSupplierCancellationTest {

    @Test
    void partialShipmentCancellationRefundsOnlyThatShipment() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        CouponService couponService = mock(CouponService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        FulfillmentService fulfillmentService = mock(FulfillmentService.class);
        RefundService refundService = mock(RefundService.class);

        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.PROCESSING);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        Shipment cancelled = shipment(100L, ShipmentStatus.CANCELLED);
        Shipment inProgress = shipment(101L, ShipmentStatus.IN_TRANSIT);
        when(shipmentRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(List.of(cancelled, inProgress));

        OrderService service = new OrderService(
                orderRepository, cartRepository, cartItemRepository, userRepository,
                inventoryService, couponService, paymentRepository, shipmentRepository,
                fulfillmentService, refundService);

        service.handleSupplierShipmentCancellation(10L, 100L);

        verify(refundService).prepareCancelledShipmentRefund(100L);
        verify(refundService, never()).prepareOrderCancellationRefund(any(), any());
        verify(couponService, never()).releaseRedemption(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void finalShipmentCancellationCancelsOrderAndRefundsRemainingBalance() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        CouponService couponService = mock(CouponService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        FulfillmentService fulfillmentService = mock(FulfillmentService.class);
        RefundService refundService = mock(RefundService.class);

        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(OrderStatus.PROCESSING);
        when(order.getCouponCode()).thenReturn("SAVE10");
        when(order.getItems()).thenReturn(List.of());
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shipment firstCancelled = shipment(100L, ShipmentStatus.CANCELLED);
        Shipment finalCancelled = shipment(101L, ShipmentStatus.CANCELLED);
        when(shipmentRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(List.of(firstCancelled, finalCancelled));

        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.REFUND_PENDING);
        when(payment.getMethod()).thenReturn(java.util.Arrays.stream(PaymentMethod.values())
                .filter(method -> method != PaymentMethod.CASH_ON_DELIVERY)
                .findFirst().orElseThrow());
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        OrderService service = new OrderService(
                orderRepository, cartRepository, cartItemRepository, userRepository,
                inventoryService, couponService, paymentRepository, shipmentRepository,
                fulfillmentService, refundService);

        service.handleSupplierShipmentCancellation(10L, 101L);

        verify(order).setStatus(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(inventoryService, never()).releaseReservedStock(any());
        verify(fulfillmentService).cancelPendingTasksForOrder(10L);
        verify(couponService).releaseRedemption("SAVE10");
        verify(refundService).prepareOrderCancellationRefund(
                10L, "All supplier shipments for the order were cancelled");
        verify(refundService, never()).prepareCancelledShipmentRefund(any());
    }

    private Shipment shipment(Long id, ShipmentStatus status) {
        Shipment shipment = mock(Shipment.class);
        when(shipment.getId()).thenReturn(id);
        when(shipment.getStatus()).thenReturn(status);
        return shipment;
    }
}
