package com.store.support.dto;

import com.store.support.entity.SupportConversation.SupportCategory;
import com.store.support.entity.SupportConversation.SupportConversationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SupportConversationResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String subject;
    private SupportCategory category;
    private SupportConversationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
    private List<SupportMessageResponse> messages;
}
