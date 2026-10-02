package com.store.category.controller;

import com.store.category.dto.CategoryRequest;
import com.store.category.dto.CategoryResponse;
import com.store.category.dto.CategoryUpdateRequest;
import com.store.category.service.CategoryService;
import com.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategories(), "Categories retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryById(id), "Category retrieved successfully"));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryBySlug(slug), "Category retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.createCategory(request), "Category created successfully"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, request), "Category updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> setActive(@PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.setActive(id, active), "Category status updated successfully"));
    }
}
