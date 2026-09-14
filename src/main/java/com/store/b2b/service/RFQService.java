package com.store.b2b.service;

import com.store.auth.entity.User;
import com.store.auth.repository.UserRepository;
import com.store.b2b.dto.RFQRequest;
import com.store.b2b.entity.RFQ;
import com.store.b2b.entity.RFQItem;
import com.store.b2b.enums.RFQStatus;
import com.store.b2b.repository.RFQRepository;
import com.store.catalog.entity.Product;
import com.store.catalog.repository.ProductRepository;
import com.store.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RFQService {

    private final RFQRepository rfqRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public RFQ createRFQ(Long userId, RFQRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        RFQ rfq = RFQ.builder()
                .title(request.getTitle())
                .notes(request.getNotes())
                .status(RFQStatus.PENDING)
                .user(user)
                .build();

        List<RFQItem> items = request.getItems().stream().map(itemReq -> {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId()));
            return RFQItem.builder()
                    .rfq(rfq)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .build();
        }).toList();

        rfq.setItems(items);
        return rfqRepository.save(rfq);
    }
}