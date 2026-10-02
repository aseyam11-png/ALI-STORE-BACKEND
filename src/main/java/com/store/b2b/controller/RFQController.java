package com.store.b2b.controller;

import com.store.b2b.dto.RFQRequest;
import com.store.b2b.entity.RFQ;
import com.store.b2b.service.RFQService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * B2B request-for-quote intake. ALI STORE is a single-merchant B2C dropshipping
 * store, not a marketplace - this module is legacy/deferred and is not part of
 * the customer-facing architecture. It is kept only because it already has
 * production-shaped code and removing it is a bigger decision than this phase's
 * scope; it must not be extended with new B2B features.
 */
@RestController
@RequestMapping("/api/v1/rfqs")
@RequiredArgsConstructor
public class RFQController {

    private final RFQService rfqService;

    @PostMapping
    public ResponseEntity<RFQ> createRFQ(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RFQRequest request) {
        return new ResponseEntity<>(rfqService.createRFQ(userDetails.getUsername(), request), HttpStatus.CREATED);
    }
}
