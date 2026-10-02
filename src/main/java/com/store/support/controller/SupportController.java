package com.store.support.controller;

import com.store.common.response.ApiResponse;
import com.store.support.dto.CreateConversationRequest;
import com.store.support.dto.SendSupportMessageRequest;
import com.store.support.dto.SupportConversationResponse;
import com.store.support.entity.SupportConversation.SupportConversationStatus;
import com.store.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> createConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        supportService.createConversation(userDetails.getUsername(), request),
                        "Support conversation created"));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<SupportConversationResponse>>> getMyConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.getMyConversations(userDetails.getUsername()),
                "Support conversations retrieved"));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> getConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.getMyConversation(userDetails.getUsername(), id),
                "Support conversation retrieved"));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody SendSupportMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.sendCustomerMessage(userDetails.getUsername(), id, request),
                "Support message sent"));
    }

    @PatchMapping("/conversations/{id}/status")
    public ResponseEntity<ApiResponse<SupportConversationResponse>> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam SupportConversationStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                supportService.updateStatus(userDetails.getUsername(), id, status),
                "Support status updated"));
    }
}
