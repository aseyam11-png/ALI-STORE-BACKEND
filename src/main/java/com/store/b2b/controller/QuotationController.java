package com.store.b2b.controller;

import com.store.b2b.dto.QuotationRequest;
import com.store.b2b.entity.Quotation;
import com.store.b2b.service.QuotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * B2B quotation intake. ALI STORE is a single-merchant B2C dropshipping store,
 * not a marketplace - this module is legacy/deferred and is not part of the
 * customer-facing architecture (see RFQController). There is no "seller" Spring
 * Security role/authentication concept in this codebase, so this endpoint is
 * locked to ADMIN rather than left open to any authenticated customer, which
 * would otherwise let any customer submit a quotation against any RFQ while
 * impersonating any sellerId. Must not be extended with new B2B features.
 */
@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class QuotationController {

    private final QuotationService quotationService;

    @PostMapping
    public ResponseEntity<Quotation> createQuotation(@Valid @RequestBody QuotationRequest request) {
        return new ResponseEntity<>(quotationService.createQuotation(request), HttpStatus.CREATED);
    }
}