package com.store.seller.controller;

import com.store.seller.dto.SellerRequest;
import com.store.seller.dto.SellerResponse;
import com.store.seller.enums.SellerStatus;
import com.store.seller.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping
    public ResponseEntity<SellerResponse> createSeller(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SellerRequest request) {
        return new ResponseEntity<>(sellerService.createSeller(userDetails.getUsername(), request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerResponse> getSellerById(@PathVariable Long id) {
        return ResponseEntity.ok(sellerService.getSellerById(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam SellerStatus status) {
        return ResponseEntity.ok(sellerService.updateStatus(id, status));
    }
}
