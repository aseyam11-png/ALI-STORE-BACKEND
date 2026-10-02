package com.store.b2b.service;

import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import com.store.b2b.dto.RFQRequest;
import com.store.b2b.entity.RFQ;
import com.store.b2b.entity.RFQItem;
import com.store.b2b.enums.RFQStatus;
import com.store.b2b.repository.RFQRepository;
import com.store.product.entity.Product;
import com.store.product.repository.ProductRepository;
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
    public RFQ createRFQ(String email, RFQRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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