package com.store.b2b.service;

import com.store.b2b.dto.QuotationRequest;
import com.store.b2b.entity.Quotation;
import com.store.b2b.entity.RFQ;
import com.store.b2b.enums.QuotationStatus;
import com.store.b2b.repository.QuotationRepository;
import com.store.b2b.repository.RFQRepository;
import com.store.common.exception.ResourceNotFoundException;
import com.store.seller.entity.Seller;
import com.store.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ALI STORE is a single-merchant B2C dropshipping store, not a seller
 * marketplace - this module is legacy/deferred and is not part of the
 * customer-facing architecture. Kept only for compilation compatibility with
 * existing production-shaped code; must not be extended with new marketplace
 * features. See QuotationController for the access-control note.
 */
@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final RFQRepository rfqRepository;
    private final SellerRepository sellerRepository;

    @Transactional
    public Quotation createQuotation(QuotationRequest request) {
        RFQ rfq = rfqRepository.findById(request.getRfqId())
                .orElseThrow(() -> new ResourceNotFoundException("RFQ not found with id: " + request.getRfqId()));

        Seller seller = sellerRepository.findById(request.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + request.getSellerId()));

        Quotation quotation = Quotation.builder()
                .rfq(rfq)
                .seller(seller)
                .totalPrice(request.getTotalPrice())
                .terms(request.getTerms())
                .status(QuotationStatus.SENT)
                .build();

        return quotationRepository.save(quotation);
    }
}