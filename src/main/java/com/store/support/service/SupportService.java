package com.store.support.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ForbiddenException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.support.dto.CreateConversationRequest;
import com.store.support.dto.SendSupportMessageRequest;
import com.store.support.dto.SupportConversationResponse;
import com.store.support.dto.SupportMessageResponse;
import com.store.support.entity.SupportConversation;
import com.store.support.entity.SupportMessage;
import com.store.support.repository.SupportConversationRepository;
import com.store.support.repository.SupportMessageRepository;
import com.store.user.entity.User;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportConversationRepository conversationRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public SupportConversationResponse createConversation(
            String email,
            CreateConversationRequest request
    ) {
        User user = resolveUser(email);

        SupportConversation conversation = conversationRepository.save(
                SupportConversation.builder()
                        .userId(user.getId())
                        .subject(request.getSubject().trim())
                        .category(request.getCategory())
                        .status(SupportConversation.SupportConversationStatus.OPEN)
                        .build()
        );

        return mapConversation(conversation);
    }

    @Transactional(readOnly = true)
    public List<SupportConversationResponse> getMyConversations(String email) {
        User user = resolveUser(email);
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .map(this::mapConversation)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportConversationResponse getMyConversation(String email, Long conversationId) {
        User user = resolveUser(email);
        SupportConversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to this conversation"));
        return mapConversation(conversation);
    }

    @Transactional
    public SupportConversationResponse sendCustomerMessage(
            String email,
            Long conversationId,
            SendSupportMessageRequest request
    ) {
        User user = resolveUser(email);
        SupportConversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to this conversation"));

        if (conversation.getStatus() == SupportConversation.SupportConversationStatus.CLOSED) {
            throw new BusinessException("Closed conversations cannot receive new messages");
        }

        messageRepository.save(
                SupportMessage.builder()
                        .conversationId(conversation.getId())
                        .senderUserId(user.getId())
                        .senderName(user.getFirstName() + " " + user.getLastName())
                        .senderRole("CUSTOMER")
                        .body(request.getBody().trim())
                        .read(false)
                        .build()
        );

        if (conversation.getStatus() == SupportConversation.SupportConversationStatus.RESOLVED) {
            conversation.setStatus(SupportConversation.SupportConversationStatus.OPEN);
        }

        conversationRepository.save(conversation);
        return mapConversation(conversation);
    }

    @Transactional
    public SupportConversationResponse sendAdminMessage(
            String email,
            Long conversationId,
            SendSupportMessageRequest request
    ) {
        User admin = resolveUser(email);
        assertAdmin(admin);

        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found"));

        messageRepository.save(
                SupportMessage.builder()
                        .conversationId(conversation.getId())
                        .senderUserId(admin.getId())
                        .senderName(admin.getFirstName() + " " + admin.getLastName())
                        .senderRole("ADMIN")
                        .body(request.getBody().trim())
                        .read(false)
                        .build()
        );

        conversation.setStatus(SupportConversation.SupportConversationStatus.PENDING);
        conversationRepository.save(conversation);
        return mapConversation(conversation);
    }

    @Transactional(readOnly = true)
    public List<SupportConversationResponse> getAllConversations(String email) {
        assertAdmin(resolveUser(email));
        return conversationRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::mapConversation)
                .toList();
    }

    @Transactional
    public SupportConversationResponse updateStatus(
            String email,
            Long conversationId,
            SupportConversation.SupportConversationStatus status
    ) {
        assertAdmin(resolveUser(email));
        SupportConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Support conversation not found"));

        conversation.setStatus(status);
        return mapConversation(conversationRepository.save(conversation));
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void assertAdmin(User user) {
        if (user.getRole() == null || !"ROLE_ADMIN".equals(user.getRole().getName())) {
            throw new ForbiddenException("Admin access is required");
        }
    }

    private SupportConversationResponse mapConversation(SupportConversation conversation) {
        User customer = userRepository.findById(conversation.getUserId()).orElse(null);

        List<SupportMessageResponse> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                        .stream()
                        .map(this::mapMessage)
                        .toList();

        return SupportConversationResponse.builder()
                .id(conversation.getId())
                .customerId(conversation.getUserId())
                .customerName(customer == null
                        ? "Customer"
                        : customer.getFirstName() + " " + customer.getLastName())
                .subject(conversation.getSubject())
                .category(conversation.getCategory())
                .status(conversation.getStatus())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .lastMessageAt(messages.isEmpty()
                        ? conversation.getUpdatedAt()
                        : messages.get(messages.size() - 1).getCreatedAt())
                .messages(messages)
                .build();
    }

    private SupportMessageResponse mapMessage(SupportMessage message) {
        return SupportMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderUserId(message.getSenderUserId())
                .senderName(message.getSenderName())
                .senderRole(message.getSenderRole())
                .body(message.getBody())
                .createdAt(message.getCreatedAt())
                .read(Boolean.TRUE.equals(message.getRead()))
                .build();
    }
}
