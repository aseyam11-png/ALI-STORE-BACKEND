package com.store.b2b.controller;

import com.store.b2b.dto.QuotationRequest;
import com.store.b2b.entity.Quotation;
import com.store.b2b.service.QuotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;

    @PostMapping
    public ResponseEntity<Quotation> createQuotation(@Valid @RequestBody QuotationRequest request) {
        return new ResponseEntity<>(quotationService.createQuotation(request), HttpStatus.CREATED);
    }
}