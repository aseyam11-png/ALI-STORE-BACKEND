package com.store.admin.controller;

import com.store.common.response.ApiResponse;
import com.store.support.dto.SendSupportMessageRequest;
import com.store.support.dto.SupportConversationResponse;
import com.store.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/support")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminSupportController {

    private final SupportService supportService;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<SupportConversationResponse>>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.getAllConversations(userDetails.getUsername()),
                "Support conversations retrieved"));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> getOne(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.getAllConversations(userDetails.getUsername()).stream()
                        .filter(item -> item.getId().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new com.store.common.exception.ResourceNotFoundException("Support conversation not found")),
                "Support conversation retrieved"));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody SendSupportMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.sendAdminMessage(userDetails.getUsername(), id, request),
                "Support message sent"));
    }

    @PatchMapping("/conversations/{id}/status")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam com.store.support.entity.SupportConversation.SupportConversationStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.updateStatus(userDetails.getUsername(), id, status),
                "Support status updated"));
    }
}
