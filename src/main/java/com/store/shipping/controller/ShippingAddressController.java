package com.store.shipping.controller;

import com.store.shipping.entity.ShippingAddress;
import com.store.shipping.service.ShippingAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping-addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @PostMapping
    public ResponseEntity<ShippingAddress> createAddress(@RequestParam Long userId, @RequestBody ShippingAddress address) {
        return new ResponseEntity<>(shippingAddressService.createAddress(userId, address), HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ShippingAddress>> getUserAddresses(@PathVariable Long userId) {
        return ResponseEntity.ok(shippingAddressService.getUserAddresses(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShippingAddress> getAddressById(@PathVariable Long id) {
        return ResponseEntity.ok(shippingAddressService.getAddressById(id));
    }
}