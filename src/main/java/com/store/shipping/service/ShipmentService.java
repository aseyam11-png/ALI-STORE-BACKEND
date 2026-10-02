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
import com.store.shipping.entity.ShippingAddress;
import com.store.shipping.repository.ShipmentRepository;
import com.store.shipping.repository.ShippingAddressRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    // Legal shipment lifecycle transitions - mirrors OrderService's ALLOWED_TRANSITIONS
    // pattern. Anything not listed (DELIVERED -> SHIPPED, CANCELLED -> DELIVERED, or any
    // transition out of a terminal state) is rejected by updateStatus.
    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ShipmentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(ShipmentStatus.PENDING, EnumSet.of(ShipmentStatus.SHIPPED, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.SHIPPED, EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.IN_TRANSIT, EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.DELIVERED, EnumSet.noneOf(ShipmentStatus.class));
        ALLOWED_TRANSITIONS.put(ShipmentStatus.CANCELLED, EnumSet.noneOf(ShipmentStatus.class));
    }

    private final ShipmentRepository shipmentRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public ShipmentResponse createShipment(ShipmentRequest request) {
        ShippingAddress address = shippingAddressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with id: " + request.getShippingAddressId()));

        Shipment shipment = Shipment.builder()
                .orderId(request.getOrderId())
                .shippingAddress(address)
                .carrier(request.getCarrier())
                .trackingNumber(request.getTrackingNumber() != null ? request.getTrackingNumber() : "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(request.getStatus() != null ? request.getStatus() : ShipmentStatus.PENDING)
                .build();

        Shipment saved = shipmentRepository.save(shipment);
        return mapToResponse(saved);
    }

    public ShipmentResponse getShipmentByOrderId(String email, Long orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("This order does not belong to you");
        }

        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order id: " + orderId));
        return mapToResponse(shipment);
    }

    @Transactional
    public ShipmentResponse updateStatus(Long shipmentId, ShipmentStatus newStatus) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with id: " + shipmentId));

        ShipmentStatus currentStatus = shipment.getStatus();
        Set<ShipmentStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(ShipmentStatus.class));
        if (!allowedNextStatuses.contains(newStatus)) {
            throw new BusinessException("Cannot transition shipment from " + currentStatus + " to " + newStatus);
        }

        shipment.setStatus(newStatus);
        if (newStatus == ShipmentStatus.SHIPPED) {
            shipment.setShippedAt(LocalDateTime.now());
        } else if (newStatus == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }

        return mapToResponse(shipmentRepository.save(shipment));
    }

    private ShipmentResponse mapToResponse(Shipment shipment) {
        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrderId())
                .trackingNumber(shipment.getTrackingNumber())
                .carrier(shipment.getCarrier())
                .status(shipment.getStatus())
                .shippingAddressId(shipment.getShippingAddress().getId())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .build();
    }
}