package com.store.finance.controller;

import com.store.common.response.ApiResponse;
import com.store.finance.dto.FinanceSummaryResponse;
import com.store.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FinanceController {
    private final FinanceService financeService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FinanceSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(
                financeService.getSummary(),
                "Finance summary retrieved successfully"));
    }
}
