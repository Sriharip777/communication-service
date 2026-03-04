package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.client.ParentServiceClient;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ParentServiceClient parentServiceClient;
    private final ConversationMapper conversationMapper;

    /**
     * Get or create conversation between two users.
     * For parents, creates/uses PARENT_DIRECT.
     * For students/teachers, creates/uses DIRECT.
     */
    @Transactional
    public Conversation getOrCreateConversation(String currentUserId, String otherUserId, String role) {
        log.info("Getting or creating conversation between {} and {} (role={})",
                currentUserId, otherUserId, role);

        if ("PARENT".equalsIgnoreCase(role)) {
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

    // parent ↔ teacher conversation, separate type
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

    // ----------------- existing methods with parent support -----------------

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
     * Get conversation by ID with parent–child authorization.
     * userRole is the role of the current user (PARENT, STUDENT, TEACHER).
     */
    public ConversationDto getConversationById(String conversationId, String userId, String userRole) {
        log.info("Getting conversation {} for user {} (role={})",
                conversationId, userId, userRole);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        List<String> participants = conversation.getParticipantIds();

        // 1) If user is a direct participant, allow
        if (participants.contains(userId)) {
            return conversationMapper.toDto(conversation, userId);
        }

        // 2) If user is a parent, allow if any of their children is a participant
        if ("PARENT".equalsIgnoreCase(userRole)) {
            List<String> childIds = parentServiceClient.getChildStudentIds(userId);
            log.info("Parent {} has children {}", userId, childIds);

            boolean anyChildInConversation = childIds.stream()
                    .anyMatch(participants::contains);

            if (anyChildInConversation) {
                log.info("Parent {} authorized via child participant", userId);
                // important: pass parentId as userId so unread counts map correctly for parent
                return conversationMapper.toDto(conversation, userId);
            }

            log.warn("Parent {} not linked to any participant in conversation {}", userId, conversationId);
            throw new IllegalArgumentException("Parent not linked to any participant in this conversation");
        }

        // 3) Non-parent not in participants -> forbidden
        log.warn("User {} is not a participant in conversation {}", userId, conversationId);
        throw new IllegalArgumentException("User is not a participant in this conversation");
    }

    /**
     * Get conversations involving any of the given childIds
     */
    public Page<ConversationDto> getChildConversations(List<String> childIds, Pageable pageable) {
        log.info("🔍 getChildConversations for childIds: {}", childIds);

        if (childIds == null || childIds.isEmpty()) {
            log.info("🔍 No childIds, returning empty");
            return Page.empty(pageable);
        }

        Page<Conversation> page =
                conversationRepository.findByAnyParticipantInChildren(childIds, pageable);
        log.info("🔍 Mongo returned {} conversations for childIds {}", page.getTotalElements(), childIds);
        page.forEach(c -> log.info("🔎 conv id={} participants={} type={}",
                c.getId(), c.getParticipantIds(), c.getType()));

        return page.map(conversation -> conversationMapper.toDto(conversation));
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