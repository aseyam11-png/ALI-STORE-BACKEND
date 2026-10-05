package com.store.shipping.controller;

import com.store.common.response.ApiResponse;
import com.store.shipping.dto.ShippingAddressRequest;
import com.store.shipping.dto.ShippingAddressResponse;
import com.store.shipping.service.ShippingAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping-addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShippingAddressResponse>> createAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ShippingAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        shippingAddressService.createAddress(userDetails.getUsername(), request),
                        "Shipping address created successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ShippingAddressResponse>>> getMyAddresses(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingAddressService.getMyAddresses(userDetails.getUsername()),
                "Shipping addresses retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShippingAddressResponse>> getAddressById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingAddressService.getMyAddressById(userDetails.getUsername(), id),
                "Shipping address retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShippingAddressResponse>> updateAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ShippingAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingAddressService.updateAddress(userDetails.getUsername(), id, request),
                "Shipping address updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        shippingAddressService.deleteAddress(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Shipping address deleted successfully"));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<ShippingAddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingAddressService.setDefaultAddress(userDetails.getUsername(), id),
                "Default shipping address updated successfully"));
    }
}
