package com.store.support.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SupportMessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderUserId;
    private String senderName;
    private String senderRole;
    private String body;
    private LocalDateTime createdAt;
    private boolean read;
}
