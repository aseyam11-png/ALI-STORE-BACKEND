package com.store.seller.service;

import com.store.common.exception.ResourceNotFoundException;
import com.store.seller.dto.SellerRequest;
import com.store.seller.dto.SellerResponse;
import com.store.seller.entity.Seller;
import com.store.seller.enums.SellerStatus;
import com.store.seller.repository.SellerRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALI STORE is a single-merchant B2C dropshipping store, not a seller
 * marketplace - this module is legacy/deferred and is not part of the
 * customer-facing architecture. Kept only for compilation compatibility with
 * existing production-shaped code; must not be extended with new marketplace
 * features (seller storefronts, payouts, commissions, etc).
 */
@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;

    @Transactional
    public SellerResponse createSeller(String email, SellerRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Seller seller = Seller.builder()
                .user(user)
                .storeName(request.getStoreName())
                .taxId(request.getTaxId())
                .status(SellerStatus.PENDING)
                .build();

        Seller saved = sellerRepository.save(seller);
        return mapToResponse(saved);
    }

    public SellerResponse getSellerById(Long id) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + id));
        return mapToResponse(seller);
    }

    @Transactional
    public SellerResponse updateStatus(Long id, SellerStatus status) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + id));
        seller.setStatus(status);
        return mapToResponse(sellerRepository.save(seller));
    }

    private SellerResponse mapToResponse(Seller seller) {
        return SellerResponse.builder()
                .id(seller.getId())
                .userId(seller.getUser().getId())
                .storeName(seller.getStoreName())
                .taxId(seller.getTaxId())
                .status(seller.getStatus())
                .createdAt(seller.getCreatedAt())
                .build();
    }
}
