package com.store.b2b.controller;

import com.store.b2b.dto.RFQRequest;
import com.store.b2b.entity.RFQ;
import com.store.b2b.service.RFQService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rfqs")
@RequiredArgsConstructor
public class RFQController {

    private final RFQService rfqService;

    @PostMapping
    public ResponseEntity<RFQ> createRFQ(
            @RequestParam Long userId,
            @Valid @RequestBody RFQRequest request) {
        return new ResponseEntity<>(rfqService.createRFQ(userId, request), HttpStatus.CREATED);
    }
}