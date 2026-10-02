package com.store.notification.controller;

import com.store.common.response.ApiResponse;
import com.store.notification.dto.NotificationResponse;
import com.store.notification.dto.SendNotificationRequest;
import com.store.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMyNotifications(userDetails.getUsername()), "Notifications retrieved successfully"));
    }

    @GetMapping("/me/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyUnreadNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMyUnreadNotifications(userDetails.getUsername()), "Unread notifications retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(notificationService.sendNotification(request), "Notification sent successfully"));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAsRead(userDetails.getUsername(), id), "Notification marked as read"));
    }
}
