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
        User user = resolveUser(email);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            shippingAddressRepository.clearDefaultForUser(user.getId());
        }

        ShippingAddress address = ShippingAddress.builder()
                .user(user)
                .street(request.getStreet().trim())
                .city(request.getCity().trim())
                .state(blankToNull(request.getState()))
                .country(request.getCountry().trim())
                .postalCode(blankToNull(request.getPostalCode()))
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        return mapToResponse(shippingAddressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<ShippingAddressResponse> getMyAddresses(String email) {
        User user = resolveUser(email);

        return shippingAddressRepository.findByUserIdOrderByIsDefaultDescIdAsc(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShippingAddressResponse getMyAddressById(String email, Long id) {
        Long userId = resolveUser(email).getId();
        return mapToResponse(findOwnedAddress(id, userId));
    }

    @Transactional
    public ShippingAddressResponse updateAddress(String email, Long id, ShippingAddressRequest request) {
        Long userId = resolveUser(email).getId();
        ShippingAddress address = findOwnedAddress(id, userId);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            shippingAddressRepository.clearDefaultForUser(userId);
            address.setIsDefault(true);
        } else if (request.getIsDefault() != null) {
            address.setIsDefault(false);
        }

        address.setStreet(request.getStreet().trim());
        address.setCity(request.getCity().trim());
        address.setState(blankToNull(request.getState()));
        address.setCountry(request.getCountry().trim());
        address.setPostalCode(blankToNull(request.getPostalCode()));

        return mapToResponse(shippingAddressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String email, Long id) {
        Long userId = resolveUser(email).getId();
        ShippingAddress address = findOwnedAddress(id, userId);
        shippingAddressRepository.delete(address);
    }

    @Transactional
    public ShippingAddressResponse setDefaultAddress(String email, Long id) {
        Long userId = resolveUser(email).getId();
        ShippingAddress address = findOwnedAddress(id, userId);

        shippingAddressRepository.clearDefaultForUser(userId);
        address.setIsDefault(true);

        return mapToResponse(shippingAddressRepository.save(address));
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ShippingAddress findOwnedAddress(Long id, Long userId) {
        return shippingAddressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ForbiddenException("You do not have access to this shipping address"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
