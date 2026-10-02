package com.store.support.repository;

import com.store.support.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
