package com.store.inventory.controller;

import com.store.common.response.ApiResponse;
import com.store.inventory.dto.SupplierInventorySyncReport;
import com.store.inventory.service.SupplierInventorySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventorySyncController {

    private final SupplierInventorySyncService supplierInventorySyncService;

    @PostMapping("/sync/{supplierId}")
    public ResponseEntity<ApiResponse<SupplierInventorySyncReport>> syncSupplier(
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(ApiResponse.success(
                supplierInventorySyncService.syncSupplier(supplierId),
                "Supplier inventory synchronized successfully"));
    }
}
