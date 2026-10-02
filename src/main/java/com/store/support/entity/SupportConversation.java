package com.store.support.entity;

import com.store.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "support_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportConversation extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SupportConversationStatus status = SupportConversationStatus.OPEN;

    public enum SupportCategory {
        ORDER, SHIPPING, PRODUCT, PAYMENT, OTHER
    }

    public enum SupportConversationStatus {
        OPEN, PENDING, RESOLVED, CLOSED
    }
}
