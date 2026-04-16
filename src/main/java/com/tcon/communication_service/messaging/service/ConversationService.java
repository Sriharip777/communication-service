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
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ParentServiceClient parentServiceClient;
    private final ConversationMapper conversationMapper;

    // ─────────────────────────────────────────────────────────────
    // Get or create conversation
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Conversation getOrCreateConversation(String currentUserId, String otherUserId, String role) {
        log.info("🔗 getOrCreateConversation: {}({}) ↔ {}", currentUserId, role, otherUserId);

        if ("PARENT".equalsIgnoreCase(role)) {
            return getOrCreateParentDirectConversation(currentUserId, otherUserId);
        } else if ("MODERATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) {
            return getOrCreateSupportConversation(currentUserId, otherUserId);
        } else {
            return getOrCreateChildConversation(currentUserId, otherUserId);
        }
    }


    // Student ↔ Teacher DIRECT conversation
    @Transactional
    public Conversation getOrCreateChildConversation(String studentId, String teacherId) {
        List<String> participants = Arrays.asList(studentId, teacherId);
        participants.sort(String::compareTo);

        return conversationRepository
                .findByParticipantIdsAndType(participants, "DIRECT")
                .orElseGet(() -> {
                    Conversation conv = new Conversation();
                    conv.setParticipantIds(participants);
                    conv.setType("DIRECT");
                    conv.setCreatedAt(LocalDateTime.now());
                    conv.setUnreadCounts(new HashMap<>());
                    log.info("✅ Created DIRECT conversation: {} ↔ {}", studentId, teacherId);
                    return conversationRepository.save(conv);
                });
    }

    // Parent ↔ Teacher PARENT_DIRECT conversation
    @Transactional
    public Conversation getOrCreateParentDirectConversation(String parentId, String teacherId) {
        List<String> participants = Arrays.asList(parentId, teacherId);
        participants.sort(String::compareTo);

        return conversationRepository
                .findByParticipantIdsAndType(participants, "PARENT_DIRECT")
                .orElseGet(() -> {
                    Conversation conv = Conversation.builder()
                            .participantIds(participants)
                            .type("PARENT_DIRECT")
                            .createdAt(LocalDateTime.now())
                            .unreadCounts(new HashMap<>())
                            .build();
                    Conversation saved = conversationRepository.save(conv);
                    log.info("✅ Created PARENT_DIRECT conversation: {} ↔ {}", parentId, teacherId);
                    return saved;
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Conversation listing per role
    // ─────────────────────────────────────────────────────────────

    // All conversations for any user (students, teachers — fallback)
    public Page<ConversationDto> getUserConversations(String userId, Pageable pageable) {
        log.info("📋 getUserConversations for: {}", userId);
        return conversationRepository
                .findByParticipantIdsContaining(userId, pageable)
                .map(conv -> conversationMapper.toDto(conv, userId));
    }

    // ✅ TEACHER: DIRECT (student chats) + PARENT_DIRECT (parent chats)
    public Page<ConversationDto> getTeacherConversations(String teacherId, Pageable pageable) {
        log.info("📋 getTeacherConversations (DIRECT + PARENT_DIRECT) for: {}", teacherId);
        return conversationRepository
                .findByParticipantAndTypeIn(teacherId, List.of("DIRECT", "PARENT_DIRECT"), pageable)
                .map(conv -> conversationMapper.toDto(conv, teacherId));
    }

    public Page<ConversationDto> getSupportConversations(String userId, Pageable pageable) {
        log.info("📋 getSupportConversations (SUPPORT_DIRECT) for: {}", userId);
        return conversationRepository
                .findByParticipantAndTypeIn(userId, List.of("SUPPORT_DIRECT", "DIRECT"), pageable)
                .map(conv -> conversationMapper.toDto(conv, userId));
    }

    // ✅ PARENT direct (My Chats with Teachers tab)
    public Page<ConversationDto> getParentTeacherConversations(String parentId, Pageable pageable) {
        log.info("📋 getParentTeacherConversations (PARENT_DIRECT) for: {}", parentId);
        return conversationRepository
                .findByParticipantAndType(parentId, "PARENT_DIRECT", pageable)
                .map(conv -> conversationMapper.toDto(conv, parentId));
    }

    // ✅ PARENT child conversations (observer mode tab)
    public Page<ConversationDto> getChildConversations(List<String> childIds, Pageable pageable) {
        log.info("📋 getChildConversations for childIds: {}", childIds);

        if (childIds == null || childIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<Conversation> page =
                conversationRepository.findByAnyParticipantInChildren(childIds, pageable);
        log.info("✅ Found {} conversations for children", page.getTotalElements());

        return page.map(conv -> conversationMapper.toDto(conv));
    }

    // ─────────────────────────────────────────────────────────────
    // Conversation by ID (with parent auth)
    // ─────────────────────────────────────────────────────────────

    public ConversationDto getConversationById(String conversationId, String userId, String userRole) {
        log.info("🔍 getConversationById: {} for {}({})", conversationId, userId, userRole);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        List<String> participants = conversation.getParticipantIds();

        // 1. Direct participant — always allowed
        if (participants.contains(userId)) {
            return conversationMapper.toDto(conversation, userId);
        }

        // 2. Parent — allowed if any child is participant
        if ("PARENT".equalsIgnoreCase(userRole)) {
            List<String> childIds = parentServiceClient.getChildStudentIds(userId);
            log.info("🔍 Parent {} children: {}", userId, childIds);

            boolean anyChildInConversation = childIds.stream()
                    .anyMatch(participants::contains);

            if (anyChildInConversation) {
                log.info("✅ Parent {} authorized via child", userId);
                return conversationMapper.toDto(conversation, userId);
            }

            log.warn("🚫 Parent {} not linked to any participant in {}", userId, conversationId);
            throw new IllegalArgumentException(
                    "Parent not linked to any participant in this conversation");
        }

        // 3. Non-participant — forbidden
        log.warn("🚫 User {} is not a participant in {}", userId, conversationId);
        throw new IllegalArgumentException("User is not a participant in this conversation");
    }

    // ─────────────────────────────────────────────────────────────
    // Existing utility methods (unchanged)
    // ─────────────────────────────────────────────────────────────

    public Page<ConversationDto> getActiveConversations(String userId, Pageable pageable) {
        log.info("📋 getActiveConversations for: {}", userId);
        return conversationRepository.findActiveConversations(userId, pageable)
                .map(conv -> conversationMapper.toDto(conv, userId));
    }

    public long countUnreadConversations(String userId) {
        return conversationRepository.countUnreadConversations(userId);
    }

    @Transactional
    public void deleteConversation(String conversationId, String userId) {
        log.info("🗑️ deleteConversation: {} by {}", conversationId, userId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        conversationRepository.delete(conversation);
        log.info("✅ Conversation deleted: {}", conversationId);
    }

    @Transactional
    public void markConversationAsRead(String conversationId, String userId) {
        log.info("📖 markConversationAsRead: {} for {}", conversationId, userId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        if (!conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant in this conversation");
        }

        conversation.resetUnreadCount(userId);
        conversationRepository.save(conversation);
        log.info("✅ Conversation {} marked as read for {}", conversationId, userId);
    }

    @Transactional
    public void updateLastMessage(String conversationId, String messageId,
                                  String content, String senderId) {
        log.info("📝 updateLastMessage for conversation {}", conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        conversation.updateLastMessage(messageId, content, senderId);
        conversationRepository.save(conversation);
        log.info("✅ Last message updated for conversation {}", conversationId);
    }

    @Transactional
    public Conversation getOrCreateSupportConversation(String supportUserId, String otherUserId) {
        List<String> participants = Arrays.asList(supportUserId, otherUserId);
        participants.sort(String::compareTo);

        return conversationRepository
                .findByParticipantIdsAndType(participants, "SUPPORT_DIRECT")
                .orElseGet(() -> {
                    Conversation conv = Conversation.builder()
                            .participantIds(participants)
                            .type("SUPPORT_DIRECT")
                            .createdAt(LocalDateTime.now())
                            .unreadCounts(new HashMap<>())
                            .build();
                    Conversation saved = conversationRepository.save(conv);
                    log.info("✅ Created SUPPORT_DIRECT conversation: {} ↔ {}", supportUserId, otherUserId);
                    return saved;
                });
    }
}