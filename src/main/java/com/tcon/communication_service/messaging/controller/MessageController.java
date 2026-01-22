package com.tcon.communication_service.messaging.controller;

import com.tcon.communication_service.messaging.dto.ConversationDto;
import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.dto.MessageSendRequest;
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

/**
 * Message Controller
 * REST API endpoints for messaging functionality
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    /**
     * Send a message
     * POST /api/messages
     */
    @PostMapping
    public ResponseEntity<MessageDto> sendMessage(
            @RequestHeader("X-User-Id") String senderId,  // ← Get senderId from header
            @Valid @RequestBody MessageSendRequest request) {
        log.info("Sending message from {} to {}", senderId, request.getReceiverId());
        MessageDto message = messageService.sendMessage(senderId, request);  // ← Fixed signature
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    /**
     * Get messages in a conversation
     * GET /api/messages/conversations/{conversationId}
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Page<MessageDto>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MessageDto> messages = messageService.getConversationMessages(conversationId, pageable);  // ← Fixed method name
        return ResponseEntity.ok(messages);
    }

    /**
     * Mark message as read
     * PUT /api/messages/{messageId}/read
     */
    @PutMapping("/{messageId}/read")
    public ResponseEntity<MessageDto> markAsRead(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId) {  // ← Changed to header
        MessageDto message = messageService.markAsRead(messageId, userId);  // ← Returns MessageDto
        return ResponseEntity.ok(message);
    }

    /**
     * Mark all messages in conversation as read
     * PUT /api/messages/conversations/{conversationId}/read
     */
    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId) {  // ← Changed to header
        messageService.markConversationAsRead(conversationId, userId);  // ← Fixed method name
        return ResponseEntity.ok().build();
    }

    /**
     * Edit a message
     * PUT /api/messages/{messageId}
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String content) {
        MessageDto message = messageService.editMessage(messageId, userId, content);
        return ResponseEntity.ok(message);
    }

    /**
     * Delete a message
     * DELETE /api/messages/{messageId}
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String messageId,
            @RequestHeader("X-User-Id") String userId) {
        messageService.deleteMessage(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get unread count for a conversation
     * GET /api/messages/conversations/{conversationId}/unread-count
     */
    @GetMapping("/conversations/{conversationId}/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId) {  // ← Changed to header
        Long count = messageService.getUnreadCount(conversationId, userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Get user conversations
     * GET /api/messages/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationDto>> getUserConversations(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ConversationDto> conversations = conversationService.getUserConversations(userId, pageable);  // ← Fixed signature
        return ResponseEntity.ok(conversations);
    }

    /**
     * Get conversation by ID
     * GET /api/messages/conversations/{conversationId}/details
     */
    @GetMapping("/conversations/{conversationId}/details")
    public ResponseEntity<ConversationDto> getConversation(
            @PathVariable String conversationId,
            @RequestHeader("X-User-Id") String userId) {  // ← Added userId for validation
        ConversationDto conversation = conversationService.getConversationById(conversationId, userId);  // ← Fixed method name
        return ResponseEntity.ok(conversation);
    }

    /**
     * Get conversation between two users (or create if not exists)
     * GET /api/messages/conversations/with/{otherUserId}
     */
    @GetMapping("/conversations/with/{otherUserId}")
    public ResponseEntity<ConversationDto> getOrCreateConversation(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String otherUserId) {
        var conversation = conversationService.getOrCreateConversation(userId, otherUserId);
        ConversationDto dto = conversationService.getConversationById(conversation.getId(), userId);
        return ResponseEntity.ok(dto);
    }
}
