package com.store.shipping.service;

import com.store.auth.entity.User;
import com.store.auth.repository.UserRepository;
import com.store.common.exception.ResourceNotFoundException;
import com.store.shipping.entity.ShippingAddress;
import com.store.shipping.repository.ShippingAddressRepository;
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
    public ShippingAddress createAddress(Long userId, ShippingAddress address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        address.setUser(user);
        return shippingAddressRepository.save(address);
    }

    public List<ShippingAddress> getUserAddresses(Long userId) {
        return shippingAddressRepository.findByUserId(userId);
    }

    public ShippingAddress getAddressById(Long id) {
        return shippingAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping Address not found with id: " + id));
    }
}