package com.store.seller.service;

import com.store.auth.entity.User;
import com.store.auth.repository.UserRepository;
import com.store.common.exception.ResourceNotFoundException;
import com.store.seller.dto.SellerRequest;
import com.store.seller.dto.SellerResponse;
import com.store.seller.entity.Seller;
import com.store.seller.enums.SellerStatus;
import com.store.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;

    @Transactional
    public SellerResponse createSeller(SellerRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

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