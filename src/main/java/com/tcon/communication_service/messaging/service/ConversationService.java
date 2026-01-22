package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.messaging.dto.ConversationDto;
import com.tcon.communication_service.messaging.entity.Conversation;
import com.tcon.communication_service.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Conversation Service
 * Business logic for conversations
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMapper conversationMapper;

    /**
     * Get or create conversation between two users
     */
    @Transactional
    public Conversation getOrCreateConversation(String userId1, String userId2) {
        log.info("Getting or creating conversation between {} and {}", userId1, userId2);

        // Sort IDs for consistent querying
        List<String> participantIds = Arrays.asList(userId1, userId2);
        participantIds.sort(String::compareTo);

        // Try to find existing conversation
        return conversationRepository.findByParticipantIds(participantIds)
                .orElseGet(() -> {
                    // Create new conversation
                    Conversation conversation = Conversation.builder()
                            .participantIds(participantIds)
                            .type("DIRECT")
                            .createdAt(LocalDateTime.now())
                            .build();

                    Conversation saved = conversationRepository.save(conversation);
                    log.info("Created new conversation: {}", saved.getId());
                    return saved;
                });
    }

    /**
     * Get user's conversations with pagination
     */
    public Page<ConversationDto> getUserConversations(String userId, Pageable pageable) {
        log.info("Getting conversations for user: {}", userId);
        return conversationRepository.findByParticipantIdsContaining(userId, pageable)
                .map(conversationMapper::toDto);
    }

    /**
     * Get conversation by ID
     */
    public ConversationDto getConversationById(String conversationId, String userId) {
        log.info("Getting conversation {} for user {}", conversationId, userId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        // Verify user is a participant
        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        return conversationMapper.toDto(conversation);
    }

    /**
     * Get active conversations (with messages)
     */
    public Page<ConversationDto> getActiveConversations(String userId, Pageable pageable) {
        log.info("Getting active conversations for user: {}", userId);
        return conversationRepository.findActiveConversations(userId, pageable)
                .map(conversationMapper::toDto);
    }

    /**
     * Count unread conversations
     */
    public long countUnreadConversations(String userId) {
        return conversationRepository.countUnreadConversations(userId);
    }

    /**
     * Delete conversation
     */
    @Transactional
    public void deleteConversation(String conversationId, String userId) {
        log.info("Deleting conversation {} by user {}", conversationId, userId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        // Verify user is a participant
        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        conversationRepository.delete(conversation);
        log.info("Conversation deleted: {}", conversationId);
    }
}
