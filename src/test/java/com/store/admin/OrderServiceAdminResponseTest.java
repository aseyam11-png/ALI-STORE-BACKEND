package com.store.admin;

import com.store.admin.dto.AdminOrderResponse;
import com.store.cart.repository.CartItemRepository;
import com.store.cart.repository.CartRepository;
import com.store.coupon.service.CouponService;
import com.store.fulfillment.service.FulfillmentService;
import com.store.inventory.service.InventoryService;
import com.store.order.entity.Order;
import com.store.order.entity.OrderItem;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.order.service.OrderService;
import com.store.payment.repository.PaymentRepository;
import com.store.product.entity.Product;
import com.store.shipping.entity.Shipment;
import com.store.shipping.repository.ShipmentRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceAdminResponseTest {

    @Test
    void adminOrderListingIncludesCustomerAndStoreProductIdentifiers() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        CartItemRepository cartItemRepository = mock(CartItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        InventoryService inventoryService = mock(InventoryService.class);
        CouponService couponService = mock(CouponService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        FulfillmentService fulfillmentService = mock(FulfillmentService.class);
        com.store.payment.service.RefundService refundService =
                mock(com.store.payment.service.RefundService.class);

        OrderService service = new OrderService(
                orderRepository, cartRepository, cartItemRepository, userRepository,
                inventoryService, couponService, paymentRepository, shipmentRepository,
                fulfillmentService, refundService);

        User customer = User.builder()
                .firstName("Ali")
                .lastName("Customer")
                .email("customer@example.com")
                .phoneNumber("+970599123456")
                .build();

        Product product = Product.builder()
                .name("Classic Watch")
                .sku("ALI-WATCH-007")
                .build();
        product.setId(77L);

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("19.99"))
                .subtotal(new BigDecimal("39.98"))
                .build();
        item.setId(701L);

        Order order = Order.builder()
                .user(customer)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("39.98"))
                .currency("USD")
                .discountAmount(BigDecimal.ZERO)
                .shippingAddress("Nablus, Palestine")
                .items(List.of(item))
                .build();
        order.setId(1001L);
        order.setCreatedAt(LocalDateTime.of(2026, 10, 23, 10, 0));
        item.setOrder(order);

        var pageable = PageRequest.of(0, 20);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(shipmentRepository.findByOrderIdInOrderByOrderIdAscIdAsc(List.of(1001L)))
                .thenReturn(List.<Shipment>of());

        var result = service.getAllAdminOrderDetails(pageable);
        AdminOrderResponse mapped = result.getContent().get(0);

        assertEquals(1, result.getTotalElements());
        assertEquals(1001L, mapped.getId());
        assertEquals("Ali Customer", mapped.getCustomerName());
        assertEquals("customer@example.com", mapped.getCustomerEmail());
        assertEquals("+970599123456", mapped.getCustomerPhone());
        assertEquals("Classic Watch", mapped.getItems().get(0).getProductName());
        assertEquals(77L, mapped.getItems().get(0).getProductId());
        assertEquals("ALI-WATCH-007", mapped.getItems().get(0).getProductSku());
        assertEquals(2, mapped.getItems().get(0).getQuantity());
    }
}
