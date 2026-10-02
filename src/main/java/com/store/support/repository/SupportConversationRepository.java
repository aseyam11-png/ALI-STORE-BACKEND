package com.store.support.repository;

import com.store.support.entity.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
    List<SupportConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<SupportConversation> findByIdAndUserId(Long id, Long userId);
    List<SupportConversation> findAllByOrderByUpdatedAtDesc();
}
