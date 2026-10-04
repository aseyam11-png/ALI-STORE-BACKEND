package com.store.shipping.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.order.entity.Order;
import com.store.order.repository.OrderRepository;
import com.store.shipping.dto.ShipmentRequest;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.repository.ShipmentRepository;
import com.store.shipping.repository.ShippingAddressRepository;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ShipmentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ShipmentStatus.PENDING, EnumSet.of(ShipmentStatus.PROCESSING, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.PROCESSING, EnumSet.of(ShipmentStatus.SHIPPED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.SHIPPED, EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.IN_TRANSIT, EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.DELIVERED, EnumSet.noneOf(ShipmentStatus.class));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.CANCELLED, EnumSet.noneOf(ShipmentStatus.class));
    }

    private final ShipmentRepository shipmentRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;

    @Transactional
    public ShipmentResponse createShipment(ShipmentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + request.getSupplierId()));

        if (supplier.getStatus() != com.store.supplier.entity.SupplierStatus.ACTIVE
                || !Boolean.TRUE.equals(supplier.getFulfillmentEnabled())) {
            throw new BusinessException("Supplier is not active for fulfillment");
        }

        if (!order.getItems().stream().anyMatch(item ->
                item.getSupplier() != null && item.getSupplier().getId().equals(supplier.getId()))) {
            throw new BusinessException("Supplier is not assigned to any item in this order");
        }

        if (shipmentRepository.existsByOrderIdAndSupplierId(order.getId(), supplier.getId())) {
            throw new BusinessException("A shipment already exists for this supplier and order");
        }

        Shipment.ShipmentBuilder builder = Shipment.builder()
                .orderId(order.getId())
                .supplier(supplier)
                .trackingNumber(request.getTrackingNumber())
                .carrier(request.getCarrier())
                .status(ShipmentStatus.PENDING)
                .shippingAddressSnapshot(request.getShippingAddressSnapshot());

        if (request.getShippingAddressId() != null) {
            var address = shippingAddressRepository.findById(request.getShippingAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with id: " + request.getShippingAddressId()));
            if (!address.getUser().getId().equals(order.getUser().getId())) {
                throw new ForbiddenException("Shipping address does not belong to the order owner");
            }
            builder.shippingAddress(address);
        }

        return mapToResponse(shipmentRepository.save(builder.build()));
    }

    public List<ShipmentResponse> getShipmentsByOrderId(String email, Long orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }

        return shipmentRepository.findByOrderIdOrderByIdAsc(orderId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShipmentResponse updateStatus(Long shipmentId, ShipmentStatus newStatus) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with id: " + shipmentId));

        Set<ShipmentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(shipment.getStatus(), EnumSet.noneOf(ShipmentStatus.class));
        if (!allowed.contains(newStatus)) {
            throw new BusinessException("Cannot transition shipment from " + shipment.getStatus() + " to " + newStatus);
        }

        shipment.setStatus(newStatus);
        if (newStatus == ShipmentStatus.SHIPPED && shipment.getShippedAt() == null) {
            shipment.setShippedAt(LocalDateTime.now());
        }
        if (newStatus == ShipmentStatus.DELIVERED && shipment.getDeliveredAt() == null) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }

        ShipmentResponse response = mapToResponse(shipmentRepository.save(shipment));
        synchronizeOrderStatus(shipment.getOrderId());
        return response;
    }

    /**
     * Order status is an aggregate view of its supplier shipments:
     * PROCESSING until shipment work starts, SHIPPED once any shipment is in transit,
     * DELIVERED only when every shipment is delivered, and CANCELLED when every
     * shipment is cancelled.
     */
    @Transactional
    public void synchronizeOrderStatus(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == com.store.order.entity.OrderStatus.CANCELLED
                || order.getStatus() == com.store.order.entity.OrderStatus.DELIVERED) {
            return;
        }

        List<Shipment> shipments = shipmentRepository.findByOrderIdOrderByIdAsc(orderId);
        if (shipments.isEmpty()) {
            return;
        }

        boolean allDelivered = shipments.stream().allMatch(s -> s.getStatus() == ShipmentStatus.DELIVERED);
        boolean allCancelled = shipments.stream().allMatch(s -> s.getStatus() == ShipmentStatus.CANCELLED);
        boolean anyInTransit = shipments.stream().anyMatch(s ->
                s.getStatus() == ShipmentStatus.SHIPPED
                        || s.getStatus() == ShipmentStatus.IN_TRANSIT
                        || s.getStatus() == ShipmentStatus.DELIVERED);

        if (allDelivered) {
            order.setStatus(com.store.order.entity.OrderStatus.DELIVERED);
        } else if (allCancelled) {
            order.setStatus(com.store.order.entity.OrderStatus.CANCELLED);
        } else if (anyInTransit && order.getStatus() == com.store.order.entity.OrderStatus.PROCESSING) {
            order.setStatus(com.store.order.entity.OrderStatus.SHIPPED);
        }

        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getBySupplierOrderId(String supplierOrderId) {
        Shipment shipment = shipmentRepository.findBySupplierOrderIdForUpdate(supplierOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found for supplier order id: " + supplierOrderId));
        return mapToResponse(shipment);
    }

    /**
     * Applies an idempotent status/tracking update received from an external supplier.
     * The supplier may repeat the same webhook safely, while backward state transitions
     * are rejected by the same shipment state machine used by admin operations.
     */
    @Transactional
    public ShipmentResponse applySupplierUpdate(
            String supplierCode,
            String supplierOrderId,
            ShipmentStatus newStatus,
            String trackingNumber,
            String carrier,
            LocalDateTime estimatedDeliveryDate) {
        Shipment shipment = shipmentRepository.findBySupplierOrderIdForUpdate(supplierOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for supplier order id: " + supplierOrderId));

        if (shipment.getSupplier() == null
                || !shipment.getSupplier().getExternalCode().equals(supplierCode)) {
            throw new ForbiddenException("Supplier is not authorized for this shipment");
        }

        if (newStatus == ShipmentStatus.PENDING) {
            throw new BusinessException("Supplier webhook cannot move a shipment back to PENDING");
        }

        ShipmentStatus currentStatus = shipment.getStatus();
        if (newStatus != currentStatus) {
            Set<ShipmentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(
                    currentStatus, EnumSet.noneOf(ShipmentStatus.class));
            if (!allowed.contains(newStatus)) {
                throw new BusinessException("Cannot transition shipment from " + currentStatus + " to " + newStatus);
            }
            shipment.setStatus(newStatus);
        }

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            shipment.setTrackingNumber(trackingNumber.trim());
        }
        if (carrier != null && !carrier.isBlank()) {
            shipment.setCarrier(carrier.trim());
        }
        if (estimatedDeliveryDate != null) {
            shipment.setEstimatedDeliveryDate(estimatedDeliveryDate);
        }
        if (newStatus == ShipmentStatus.SHIPPED && shipment.getShippedAt() == null) {
            shipment.setShippedAt(LocalDateTime.now());
        }
        if (newStatus == ShipmentStatus.DELIVERED && shipment.getDeliveredAt() == null) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }

        ShipmentResponse response = mapToResponse(shipmentRepository.save(shipment));
        synchronizeOrderStatus(shipment.getOrderId());
        return response;
    }

    private ShipmentResponse mapToResponse(Shipment shipment) {
        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrderId())
                .trackingNumber(shipment.getTrackingNumber())
                .carrier(shipment.getCarrier())
                .status(shipment.getStatus())
                .shippingAddressId(shipment.getShippingAddress() != null ? shipment.getShippingAddress().getId() : null)
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .build();
    }
}
