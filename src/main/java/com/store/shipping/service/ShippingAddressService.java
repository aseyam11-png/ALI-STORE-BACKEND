package com.store.shipping.service;

import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.shipping.dto.ShippingAddressRequest;
import com.store.shipping.dto.ShippingAddressResponse;
import com.store.shipping.entity.ShippingAddress;
import com.store.shipping.repository.ShippingAddressRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private final ShippingAddressRepository shippingAddressRepository;
    private final UserRepository userRepository;

    @Transactional
    public ShippingAddressResponse createAddress(String email, ShippingAddressRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShippingAddress address = ShippingAddress.builder()
                .user(user)
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        return mapToResponse(shippingAddressRepository.save(address));
    }

    public List<ShippingAddressResponse> getMyAddresses(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return shippingAddressRepository.findByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShippingAddressResponse updateAddress(String email, Long id, ShippingAddressRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShippingAddress address = shippingAddressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to this shipping address"));

        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPostalCode(request.getPostalCode());

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            shippingAddressRepository.findByUserId(user.getId()).forEach(existing -> {
                if (!existing.getId().equals(id)) {
                    existing.setIsDefault(false);
                }
            });
            address.setIsDefault(true);
        }

        return mapToResponse(shippingAddressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShippingAddress address = shippingAddressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to this shipping address"));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        shippingAddressRepository.delete(address);

        if (wasDefault) {
            shippingAddressRepository.findByUserId(user.getId()).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsDefault(true);
                        shippingAddressRepository.save(next);
                    });
        }
    }

    @Transactional
    public ShippingAddressResponse setDefaultAddress(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShippingAddress address = shippingAddressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to this shipping address"));

        shippingAddressRepository.findByUserId(user.getId()).forEach(existing ->
                existing.setIsDefault(existing.getId().equals(id)));

        return mapToResponse(shippingAddressRepository.save(address));
    }

    public ShippingAddressResponse getMyAddressById(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShippingAddress address = shippingAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with id: " + id));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this shipping address");
        }

        return mapToResponse(address);
    }

    private ShippingAddressResponse mapToResponse(ShippingAddress address) {
        return ShippingAddressResponse.builder()
                .id(address.getId())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .postalCode(address.getPostalCode())
                .isDefault(address.getIsDefault())
                .build();
    }
}
