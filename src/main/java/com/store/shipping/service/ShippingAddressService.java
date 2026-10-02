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

@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    private final ShippingAddressRepository shippingAddressRepository;
    private final UserRepository userRepository;

    @Transactional
    public ShippingAddressResponse createAddress(String email, ShippingAddressRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            shippingAddressRepository.clearDefaultForUser(user.getId());
        }

        ShippingAddress address = ShippingAddress.builder()
                .user(user)
                .label(request.getLabel())
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .street(request.getStreet().trim())
                .area(request.getArea())
                .building(request.getBuilding())
                .apartment(request.getApartment())
                .notes(request.getNotes())
                .city(request.getCity().trim())
                .state(request.getState() == null ? null : request.getState().trim())
                .country(request.getCountry().trim())
                .postalCode(request.getPostalCode() == null ? null : request.getPostalCode().trim())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        return mapToResponse(shippingAddressRepository.save(address));
    }

    public List<ShippingAddressResponse> getMyAddresses(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return shippingAddressRepository.findByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ShippingAddressResponse getMyAddressById(String email, Long id) {
        return mapToResponse(getOwnedAddress(email, id));
    }

    @Transactional
    public ShippingAddressResponse updateAddress(
            String email,
            Long id,
            ShippingAddressRequest request) {

        ShippingAddress address = getOwnedAddress(email, id);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            shippingAddressRepository.clearDefaultForUser(address.getUser().getId());
        }

        address.setLabel(request.getLabel());
        address.setRecipientName(request.getRecipientName());
        address.setPhone(request.getPhone());
        address.setStreet(request.getStreet().trim());
        address.setArea(request.getArea());
        address.setBuilding(request.getBuilding());
        address.setApartment(request.getApartment());
        address.setNotes(request.getNotes());
        address.setCity(request.getCity().trim());
        address.setState(request.getState() == null ? null : request.getState().trim());
        address.setCountry(request.getCountry().trim());
        address.setPostalCode(request.getPostalCode() == null ? null : request.getPostalCode().trim());
        address.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));

        return mapToResponse(shippingAddressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String email, Long id) {
        ShippingAddress address = getOwnedAddress(email, id);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        Long userId = address.getUser().getId();

        shippingAddressRepository.delete(address);

        if (wasDefault) {
            shippingAddressRepository.findFirstByUserIdOrderByIdAsc(userId)
                    .ifPresent(next -> {
                        next.setIsDefault(true);
                        shippingAddressRepository.save(next);
                    });
        }
    }

    @Transactional
    public ShippingAddressResponse setDefaultAddress(String email, Long id) {
        ShippingAddress address = getOwnedAddress(email, id);
        shippingAddressRepository.clearDefaultForUser(address.getUser().getId());
        address.setIsDefault(true);
        return mapToResponse(shippingAddressRepository.save(address));
    }

    private ShippingAddress getOwnedAddress(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShippingAddress address = shippingAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found with id: " + id));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not have access to this shipping address");
        }

        return address;
    }

    private ShippingAddressResponse mapToResponse(ShippingAddress address) {
        return ShippingAddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .street(address.getStreet())
                .area(address.getArea())
                .building(address.getBuilding())
                .apartment(address.getApartment())
                .notes(address.getNotes())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .postalCode(address.getPostalCode())
                .isDefault(address.getIsDefault())
                .build();
    }
}
