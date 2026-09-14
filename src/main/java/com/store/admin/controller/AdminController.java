package com.store.admin.controller;

import com.store.admin.dto.UpdateUserRoleRequest;
import com.store.admin.dto.UserResponse;
import com.store.admin.service.AdminService;
import com.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllUsers(), "Users retrieved successfully"));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateUserRole(userId, request), "User role updated successfully"));
    }

    @PatchMapping("/users/{userId}/toggle-status")
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.toggleUserStatus(userId), "User status updated successfully"));
    }
}