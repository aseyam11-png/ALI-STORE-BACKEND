package com.store.supplier.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.supplier.dto.SupplierRequest;
import com.store.supplier.dto.SupplierResponse;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/suppliers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SupplierAdminController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getAll(pageable), "Suppliers retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.getById(id), "Supplier retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(supplierService.create(request), "Supplier created successfully"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.update(id, request), "Supplier updated successfully"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam SupplierStatus status) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.updateStatus(id, status), "Supplier status updated successfully"));
    }
}
