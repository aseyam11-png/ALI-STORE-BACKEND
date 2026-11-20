package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.product.dto.ProductAdminResponse;
import com.store.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductAdminResponse>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(productService.getAllForAdmin(pageable), "Products retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductAdminResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getForAdmin(id), "Product retrieved successfully"));
    }
}
