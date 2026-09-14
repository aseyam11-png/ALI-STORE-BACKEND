package com.store.shipping.service;

import com.store.common.exception.ResourceNotFoundException;
import com.store.shipping.dto.ShipmentRequest;
import com.store.shipping.dto.ShipmentResponse;
import com.store.shipping.entity.Shipment;
import com.store.shipping.entity.ShipmentStatus;
import com.store.shipping.entity.ShippingAddress;
import com.store.shipping.repository.ShipmentRepository;
import com.store.shipping.repository.ShippingAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShippingAddressRepository shippingAddressRepository;

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

    public ShipmentResponse getShipmentByOrderId(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order id: " + orderId));
        return mapToResponse(shipment);
    }

    @Transactional
    public ShipmentResponse updateStatus(Long shipmentId, ShipmentStatus status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found with id: " + shipmentId));

        shipment.setStatus(status);
        if (status == ShipmentStatus.SHIPPED) {
            shipment.setShippedAt(LocalDateTime.now());
        } else if (status == ShipmentStatus.DELIVERED) {
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