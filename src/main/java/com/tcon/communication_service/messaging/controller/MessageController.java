package com.tcon.communication_service.messaging.controller;

import com.tcon.communication_service.client.UserServiceClient;
import com.tcon.communication_service.client.ParentServiceClient;
import com.tcon.communication_service.messaging.dto.ContactDto;
import com.tcon.communication_service.messaging.dto.ConversationDto;
import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.dto.MessageSendRequest;
import com.tcon.communication_service.messaging.exception.ParentAccessDeniedException;
import com.tcon.communication_service.messaging.service.ConversationService;
import com.tcon.communication_service.messaging.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final UserServiceClient userServiceClient;
    private final ParentServiceClient parentServiceClient;

    // ─────────────────────────────────────────────────────────────
    // Send message
    // ─────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(
            @RequestHeader("X-User-Id") String senderId,
            @RequestHeader("X-User-Role") String senderRole,
            @Valid @RequestBody MessageSendRequest request) {

        log.info("📤 Sending message from {} ({}) to {}", senderId, senderRole, request.getReceiverId());
        try {
            MessageDto message = messageService.sendMessage(senderId, senderRole, request);
            log.info("✅ Message sent: {}", message.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(message);
        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Send blocked: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Get messages in a conversation
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Page<MessageDto>> getMessages(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("📖 Loading messages for {} ({}) - conv: {}", userId, userRole, conversationId);
        try {
            if ("PARENT".equalsIgnoreCase(userRole)) {
                messageService.validateParentAccess(userId, conversationId);
            }
            Pageable pageable = PageRequest.of(page, size);
            Page<MessageDto> messages = messageService.getConversationMessages(conversationId, pageable);
            log.debug("✅ Loaded {} messages", messages.getTotalElements());
            return ResponseEntity.ok(messages);
        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Parent access denied: {} -> {}", userId, conversationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Mark single message as read (parents blocked)
    // ─────────────────────────────────────────────────────────────
    @PutMapping("/{messageId}/read")
    public ResponseEntity<MessageDto> markAsRead(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            if ("PARENT".equalsIgnoreCase(userRole)) {
                throw new ParentAccessDeniedException("Parents cannot mark messages as read");
            }
            MessageDto message = messageService.markAsRead(messageId, userId, userRole);
            return ResponseEntity.ok(message);
        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Mark read blocked: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Mark conversation as read (parents blocked)
    // ─────────────────────────────────────────────────────────────
    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            if ("PARENT".equalsIgnoreCase(userRole)) {
                throw new ParentAccessDeniedException("Parents cannot mark conversations as read");
            }
            messageService.markConversationAsRead(conversationId, userId, userRole);
            return ResponseEntity.ok().build();
        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Mark conv read blocked: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Edit message (parents blocked)
    // ─────────────────────────────────────────────────────────────
    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam String content) {

        try {
            if ("PARENT".equalsIgnoreCase(userRole)) {
                throw new ParentAccessDeniedException("Parents cannot edit messages");
            }
            MessageDto message = messageService.editMessage(messageId, userId, userRole, content);
            return ResponseEntity.ok(message);
        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Edit blocked: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Delete message (parents blocked)
    // ─────────────────────────────────────────────────────────────
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            if ("PARENT".equalsIgnoreCase(userRole)) {
                throw new ParentAccessDeniedException("Parents cannot delete messages");
            }
            messageService.deleteMessage(messageId, userId, userRole);
            return ResponseEntity.noContent().build();
        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Delete blocked: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Unread count
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations/{conversationId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        Long count = messageService.getUnreadCount(conversationId, userId, userRole);
        return ResponseEntity.ok(count);
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ GET /conversations — child conversations (PARENT observer mode)
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationDto>> getUserConversations(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("📋 Loading conversations for {} ({})", userId, userRole);
        Pageable pageable = PageRequest.of(page, size);

        try {
            if ("PARENT".equalsIgnoreCase(userRole)) {
                List<String> childIds = parentServiceClient.getChildStudentIds(userId);
                log.info("👨‍👩‍👧 Parent {} children: {}", userId, childIds);

                if (childIds == null || childIds.isEmpty()) {
                    return ResponseEntity.ok(Page.empty(pageable));
                }

                // ✅ ADD THIS
                if ("MODERATOR".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
                    Page<ConversationDto> conversations =
                            conversationService.getSupportConversations(userId, pageable);
                }

                    Page<ConversationDto> convos = conversationService.getChildConversations(childIds, pageable);
                log.info("✅ Found {} child conversations", convos.getTotalElements());
                return ResponseEntity.ok(convos);
            }

            Page<ConversationDto> conversations = conversationService.getUserConversations(userId, pageable);
            return ResponseEntity.ok(conversations);

        } catch (Exception e) {
            log.error("❌ Error loading conversations for {}: {}", userId, e.getMessage(), e);
            if ("PARENT".equalsIgnoreCase(userRole)) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Page.empty(pageable));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ GET /conversations/parent-direct — parent's own teacher chats
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations/parent-direct")
    public ResponseEntity<Page<ConversationDto>> getParentDirectConversations(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!"PARENT".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("📋 Loading PARENT_DIRECT conversations for {}", userId);
        Pageable pageable = PageRequest.of(page, size);
        Page<ConversationDto> conversations =
                conversationService.getParentTeacherConversations(userId, pageable);
        return ResponseEntity.ok(conversations);
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ GET /conversations/teacher/direct — teacher sees all (DIRECT + PARENT_DIRECT)
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations/teacher/direct")
    public ResponseEntity<Page<ConversationDto>> getTeacherDirectConversations(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!"TEACHER".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("📋 Loading teacher conversations (DIRECT + PARENT_DIRECT) for {}", userId);
        Pageable pageable = PageRequest.of(page, size);
        Page<ConversationDto> conversations =
                conversationService.getTeacherConversations(userId, pageable);
        return ResponseEntity.ok(conversations);
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ GET /conversations/with/{otherUserId} — get or create conversation
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations/with/{otherUserId}")
    public ResponseEntity<ConversationDto> getOrCreateConversation(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable String otherUserId) {

        log.info("🔗 getOrCreateConversation: {}({}) ↔ {}", userId, userRole, otherUserId);
        var conversation = conversationService.getOrCreateConversation(userId, otherUserId, userRole);
        ConversationDto dto = conversationService.getConversationById(conversation.getId(), userId, userRole);
        return ResponseEntity.ok(dto);
    }

    // ─────────────────────────────────────────────────────────────
    // Conversation details
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/conversations/{conversationId}/details")
    public ResponseEntity<ConversationDto> getConversation(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        try {
            ConversationDto conversation =
                    conversationService.getConversationById(conversationId, userId, userRole);
            return ResponseEntity.ok(conversation);
        } catch (RuntimeException e) {
            log.warn("🚫 Conversation details denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
    }


    // ─────────────────────────────────────────────────────────────
    // Contacts
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/contacts")
    public ResponseEntity<List<ContactDto>> getMyContacts(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {

        log.info("📇 Getting contacts for user: {}, role: {}", userId, userRole);
        try {
            // ✅ ADD THIS - moderator and admin can message anyone
            if ("MODERATOR".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole)) {
                List<ContactDto> contacts = userServiceClient.getContacts(userId, userRole);
                return ResponseEntity.ok(contacts);
            }

            List<ContactDto> contacts = userServiceClient.getContacts(userId, userRole);
            log.info("✅ Found {} contacts", contacts.size());
            return ResponseEntity.ok(contacts);
        } catch (Exception e) {
            log.error("❌ Error getting contacts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }

}