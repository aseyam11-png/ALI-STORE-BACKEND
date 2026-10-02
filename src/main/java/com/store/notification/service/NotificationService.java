package com.store.notification.service;

import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.notification.dto.NotificationResponse;
import com.store.notification.dto.SendNotificationRequest;
import com.store.notification.entity.Notification;
import com.store.notification.repository.NotificationRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public List<NotificationResponse> getMyNotifications(String email) {
        Long userId = resolveUserId(email);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getMyUnreadNotifications(String email) {
        Long userId = resolveUserId(email);
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public NotificationResponse sendNotification(SendNotificationRequest request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .message(request.getMessage())
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    @Transactional
    public NotificationResponse markAsRead(String email, Long notificationId) {
        Long userId = resolveUserId(email);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new ForbiddenException("This notification does not belong to you");
        }

        notification.setIsRead(true);
        Notification updated = notificationRepository.save(notification);
        return mapToResponse(updated);
    }

    private Long resolveUserId(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
