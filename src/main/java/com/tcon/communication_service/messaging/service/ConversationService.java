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
     * Get or create conversation between two users.
     * For parents, creates/uses PARENT_DIRECT.
     * For students/teachers, creates/uses DIRECT.
     */
    @Transactional
    public Conversation getOrCreateConversation(String currentUserId, String otherUserId) {
        log.info("Getting or creating conversation between {} and {}", currentUserId, otherUserId);

        String role = getUserRole(currentUserId); // "PARENT", "STUDENT", "TEACHER"

        if ("PARENT".equals(role)) {
            return getOrCreateParentDirectConversation(currentUserId, otherUserId);
        } else {
            return getOrCreateChildConversation(currentUserId, otherUserId);
        }
    }

    // ----------------- internal helpers -----------------

    // existing behaviour for student/teacher chats
    private Conversation getOrCreateChildConversation(String userId1, String userId2) {
        List<String> participantIds = Arrays.asList(userId1, userId2);
        participantIds.sort(String::compareTo);

        return conversationRepository
                .findByParticipantIdsAndType(participantIds, "DIRECT")
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .participantIds(participantIds)
                            .type("DIRECT")
                            .createdAt(LocalDateTime.now())
                            .build();

                    Conversation saved = conversationRepository.save(conversation);
                    log.info("Created new DIRECT conversation: {}", saved.getId());
                    return saved;
                });
    }

    // new parent ↔ teacher conversation, separate type
    private Conversation getOrCreateParentDirectConversation(String parentId, String teacherId) {
        List<String> participantIds = Arrays.asList(parentId, teacherId);
        participantIds.sort(String::compareTo);

        return conversationRepository
                .findByParticipantIdsAndType(participantIds, "PARENT_DIRECT")
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .participantIds(participantIds)
                            .type("PARENT_DIRECT")
                            .createdAt(LocalDateTime.now())
                            .build();

                    Conversation saved = conversationRepository.save(conversation);
                    log.info("Created new PARENT_DIRECT conversation: {}", saved.getId());
                    return saved;
                });
    }

    // TODO: wire this to your auth/user service
    private String getUserRole(String userId) {
        // temporary stub – replace with Feign client / DB lookup
        // e.g. userServiceClient.getUserRole(userId)
        return "STUDENT";
    }

    // ----------------- existing methods unchanged -----------------

    /**
     * Get user's conversations with pagination
     * Pass userId to mapper for user-specific unread count
     */
    public Page<ConversationDto> getUserConversations(String userId, Pageable pageable) {
        log.info("Getting conversations for user: {}", userId);
        return conversationRepository.findByParticipantIdsContaining(userId, pageable)
                .map(conversation -> conversationMapper.toDto(conversation, userId));
    }

    /**
     * Get conversation by ID
     * Pass userId to mapper for user-specific unread count
     */
    public ConversationDto getConversationById(String conversationId, String userId) {
        log.info("Getting conversation {} for user {}", conversationId, userId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        return conversationMapper.toDto(conversation, userId);
    }

    // for parent observer: child conversations
    public Page<ConversationDto> getChildConversations(List<String> childIds, Pageable pageable) {
        log.info("🔍 getChildConversations for childIds: {}", childIds);

        Page<Conversation> page =
                conversationRepository.findByAnyParticipantInChildren(childIds, pageable);
        log.info("🔍 Mongo returned {} conversations for childIds {}",
                page.getTotalElements(), childIds);

        return page.map(conversation -> conversationMapper.toDto(conversation, childIds.get(0)));
    }

    /**
     * Get active conversations (with messages)
     */
    public Page<ConversationDto> getActiveConversations(String userId, Pageable pageable) {
        log.info("Getting active conversations for user: {}", userId);
        return conversationRepository.findActiveConversations(userId, pageable)
                .map(conversation -> conversationMapper.toDto(conversation, userId));
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

        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        conversationRepository.delete(conversation);
        log.info("Conversation deleted: {}", conversationId);
    }

    /**
     * Mark conversation as read for a user
     */
    @Transactional
    public void markConversationAsRead(String conversationId, String userId) {
        log.info("Marking conversation {} as read for user {}", conversationId, userId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        conversation.resetUnreadCount(userId);
        conversationRepository.save(conversation);

        log.info("Conversation {} marked as read for user {}", conversationId, userId);
    }

    /**
     * Update conversation's last message
     */
    @Transactional
    public void updateLastMessage(String conversationId, String messageId, String content, String senderId) {
        log.info("Updating last message for conversation {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        conversation.updateLastMessage(messageId, content, senderId);
        conversationRepository.save(conversation);

        log.info("Updated last message for conversation {}", conversationId);
    }
}
