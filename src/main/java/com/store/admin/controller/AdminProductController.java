package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.common.response.PageResponse;
import com.store.product.dto.ProductResponse;
import com.store.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only catalog reads that need to see every product status (DRAFT/ACTIVE/ARCHIVED).
 * Mutations (create/update/archive) stay on {@code /api/v1/products} to avoid duplicating
 * the same write endpoints under two paths - only the "see everything, not just ACTIVE"
 * read behavior needed a separate route.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(productService.getAllForAdmin(pageable), "Products retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getForAdmin(id), "Product retrieved successfully"));
    }
}
