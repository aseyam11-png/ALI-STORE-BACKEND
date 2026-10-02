package com.store.order.service;

import com.store.cart.entity.Cart;
import com.store.cart.entity.CartItem;
import com.store.cart.repository.CartItemRepository;
import com.store.cart.repository.CartRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.PageResponse;
import com.store.coupon.entity.Coupon;
import com.store.coupon.service.CouponService;
import com.store.fulfillment.service.FulfillmentService;
import com.store.inventory.service.InventoryService;
import com.store.order.dto.OrderItemResponse;
import com.store.order.dto.OrderRequest;
import com.store.order.dto.OrderResponse;
import com.store.order.dto.OrderStatusUpdateRequest;
import com.store.order.dto.ShipmentSummaryResponse;
import com.store.order.entity.Order;
import com.store.order.entity.OrderItem;
import com.store.order.entity.OrderStatus;
import com.store.order.repository.OrderRepository;
import com.store.payment.entity.Payment;
import com.store.payment.entity.PaymentMethod;
import com.store.payment.entity.PaymentStatus;
import com.store.payment.repository.PaymentRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import com.store.shipping.entity.Shipment;
import com.store.shipping.repository.ShipmentRepository;
import com.store.shipping.service.ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final FulfillmentService fulfillmentService;
    private final ShippingQuoteService shippingQuoteService;

    @Transactional
    public OrderResponse createOrder(String email, OrderRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Cart cart = cartRepository.findWithLockByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Cart is empty"));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessException("Cannot place order with an empty cart");
        }

        BigDecimal subtotal = cartItems.stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String couponCode = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            Coupon coupon = couponService.validateAndGetCoupon(request.getCouponCode());
            couponService.checkMinimumOrder(coupon, subtotal);
            couponCode = coupon.getCode();
            discountAmount = subtotal.multiply(coupon.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (discountAmount.compareTo(subtotal) > 0) {
                discountAmount = subtotal;
            }
            couponService.redeem(coupon);
        }

        var shippingQuote = shippingQuoteService.quote(subtotal.subtract(discountAmount), request.getShippingMethod());
        BigDecimal totalAmount = subtotal.subtract(discountAmount).add(shippingQuote.getAmount());

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .totalAmount(totalAmount)
                .couponCode(couponCode)
                .discountAmount(discountAmount)
                .shippingAmount(shippingQuote.getAmount())
                .shippingMethod(shippingQuote.getMethod())
                .build();

        List<OrderItem> orderItems = cartItems.stream().map(cartItem -> {
            var product = cartItem.getProduct();

            if (product.getSupplier() == null || product.getSupplierSku() == null || product.getSupplierSku().isBlank()
                    || product.getSupplierCost() == null) {
                throw new BusinessException("Product is not configured for supplier fulfillment: " + product.getId());
            }

            inventoryService.reserveStock(product.getId(), cartItem.getQuantity());

            BigDecimal unitPrice = product.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            return OrderItem.builder()
                    .order(order)
                    .product(product)
                    .supplier(product.getSupplier())
                    .supplierSku(product.getSupplierSku())
                    .supplierUnitCost(product.getSupplierCost())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(itemSubtotal)
                    .build();
        }).collect(Collectors.toList());

        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);
        cartItemRepository.deleteAll(cartItems);
        return mapToResponse(savedOrder);
    }

    public PageResponse<OrderResponse> getUserOrders(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Page<OrderResponse> page = orderRepository.findByUserId(user.getId(), pageable).map(this::mapToResponse);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public OrderResponse getOrderById(String email, Long orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }

        return mapToResponse(order);
    }

    public PageResponse<OrderResponse> getAllOrdersForAdmin(Pageable pageable) {
        Page<OrderResponse> page = orderRepository.findAll(pageable).map(this::mapToResponse);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        Set<OrderStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(
                currentStatus, EnumSet.noneOf(OrderStatus.class));

        if (!allowedNextStatuses.contains(newStatus)) {
            throw new BusinessException("Cannot transition order from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == OrderStatus.PROCESSING) {
            if (order.getItems().stream().anyMatch(item ->
                    item.getSupplier() == null
                            || item.getSupplierSku() == null
                            || item.getSupplierSku().isBlank()
                            || item.getSupplierUnitCost() == null
                            || item.getSupplierUnitCost().signum() <= 0)) {
                throw new BusinessException("Order cannot enter fulfillment without a valid external supplier mapping");
            }

            Payment payment = paymentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException("Order cannot start fulfillment before a payment method is recorded"));

            boolean cod = payment.getMethod() == PaymentMethod.CASH_ON_DELIVERY;
            boolean confirmed = payment.getStatus() == PaymentStatus.COMPLETED;
            if (!cod && !confirmed) {
                throw new BusinessException("Order cannot start fulfillment before payment is confirmed");
            }
        }

        if (newStatus == OrderStatus.CANCELLED) {
            releaseOrderReservations(order);
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        if (newStatus == OrderStatus.PROCESSING) {
            fulfillmentService.enqueueForOrder(orderId);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public void commitOrderReservations(Order order) {
        order.getItems().forEach(item ->
                inventoryService.commitReservedStock(item.getProduct().getId(), item.getQuantity()));
    }

    @Transactional
    public void releaseOrderReservations(Order order) {
        order.getItems().forEach(item -> {
            try {
                inventoryService.releaseReservedStock(item.getProduct().getId(), item.getQuantity());
            } catch (BusinessException ignored) {
                // Reservation may already have been committed after supplier acceptance.
                // Cancellation must not corrupt inventory a second time.
            }
        });
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream().map(item ->
                OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .price(item.getUnitPrice())
                        .subTotal(item.getSubtotal())
                        .build()
        ).collect(Collectors.toList());

        List<ShipmentSummaryResponse> shipmentResponses = shipmentRepository.findByOrderIdOrderByIdAsc(order.getId())
                .stream().map(this::mapShipment)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .couponCode(order.getCouponCode())
                .discountAmount(order.getDiscountAmount())
                .shippingAmount(order.getShippingAmount())
                .shippingMethod(order.getShippingMethod())
                .paymentStatus(paymentRepository.findByOrderId(order.getId()).map(p -> p.getStatus().name()).orElse("PENDING"))
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .shipments(shipmentResponses)
                .build();
    }

    private ShipmentSummaryResponse mapShipment(Shipment shipment) {
        return ShipmentSummaryResponse.builder()
                .id(shipment.getId())
                .status(shipment.getStatus())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .build();
    }
}
