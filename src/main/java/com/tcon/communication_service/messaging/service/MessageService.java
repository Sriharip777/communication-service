package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.client.ParentServiceClient;
import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.dto.MessageSendRequest;
import com.tcon.communication_service.messaging.entity.*;
import com.tcon.communication_service.messaging.event.MessageEventPublisher;
import com.tcon.communication_service.messaging.exception.ParentAccessDeniedException;
import com.tcon.communication_service.messaging.repository.ConversationRepository;
import com.tcon.communication_service.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final MessageMapper messageMapper;
    private final MessageEventPublisher messageEventPublisher;
    private final ParentServiceClient parentServiceClient;

    /**
     * ✅ FIXED: Accepts senderRole - used by both REST and WebSocket controllers
     */
    @Transactional
    public MessageDto sendMessage(String senderId, String senderRole, MessageSendRequest request) {
        String receiverId = request.getReceiverId();

        var conversation = conversationService.getOrCreateConversation(
                senderId, receiverId, senderRole   // use these names
        );

        if ("PARENT".equals(senderRole)) {
            if (!conversation.getParticipantIds().contains(senderId)) {
                // Parent is not participant → trying to send into child/teacher convo
                throw new ParentAccessDeniedException("Parents cannot send messages in child conversations");
            }
            // otherwise parent is a participant in this conversation (parent-direct) → allowed
        }


        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .receiverId(request.getReceiverId())
                .content(request.getContent())
                .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                .status(MessageStatus.SENT)
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .mimeType(request.getMimeType())
                .replyToMessageId(request.getReplyToMessageId())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .isDeleted(false)
                .isEdited(false)
                .build();

        if (request.getReplyToMessageId() != null) {
            messageRepository.findById(request.getReplyToMessageId())
                    .ifPresent(replyTo -> message.setReplyToContent(replyTo.getContent()));
        }

        Message saved = messageRepository.save(message);
        conversationService.updateLastMessage(conversation.getId(), saved.getId(),
                saved.getContent(), senderId);

        messageEventPublisher.publishMessageSent(saved);
        log.info("✅ Message sent: {}", saved.getId());
        return messageMapper.toDto(saved);
    }

    /**
     * Get messages in a conversation (parents can read)
     */
    public Page<MessageDto> getConversationMessages(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        conversationId, pageable)
                .map(messageMapper::toDto);
    }

    /**
     * ✅ Mark message as read (parents blocked)
     */
    @Transactional
    public MessageDto markAsRead(String messageId, String userId, String userRole) {
        log.info("Marking message {} as read by {} ({})", messageId, userId, userRole);

        if ("PARENT".equals(userRole)) {
            throw new ParentAccessDeniedException("Parents cannot mark messages as read");
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getReceiverId().equals(userId)) {
            throw new IllegalArgumentException("Only receiver can mark message as read");
        }

        if (message.getStatus() != MessageStatus.READ) {
            message.markAsRead();
            Message saved = messageRepository.save(message);
            conversationService.markConversationAsRead(message.getConversationId(), userId);
            messageEventPublisher.publishMessageRead(saved);
            return messageMapper.toDto(saved);
        }

        return messageMapper.toDto(message);
    }

    /**
     * ✅ Mark all messages in conversation as read (parents blocked)
     */
    @Transactional
    public void markConversationAsRead(String conversationId, String userId, String userRole) {
        log.info("Marking conversation {} as read by {} ({})", conversationId, userId, userRole);

        if ("PARENT".equals(userRole)) {
            throw new ParentAccessDeniedException("Parents cannot mark conversations as read");
        }

        List<Message> unreadMessages = messageRepository
                .findByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
                        conversationId, MessageStatus.SENT, userId);

        unreadMessages.forEach(msg -> {
            msg.markAsRead();
            messageEventPublisher.publishMessageRead(msg);
        });

        messageRepository.saveAll(unreadMessages);
        conversationService.markConversationAsRead(conversationId, userId);
    }

    /**
     * ✅ Edit message (parents blocked)
     */
    @Transactional
    public MessageDto editMessage(String messageId, String userId, String userRole, String newContent) {
        log.info("Editing message {} by {} ({})", messageId, userId, userRole);

        if ("PARENT".equals(userRole)) {
            throw new ParentAccessDeniedException("Parents cannot edit messages");
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Only sender can edit the message");
        }

        if (!message.canBeEdited()) {
            throw new IllegalStateException("Message cannot be edited");
        }

        message.edit(newContent);
        Message saved = messageRepository.save(message);

        conversationRepository.findById(message.getConversationId())
                .ifPresent(conv -> {
                    if (messageId.equals(conv.getLastMessageId())) {
                        conversationService.updateLastMessage(
                                conv.getId(), saved.getId(),
                                saved.getContent(), saved.getSenderId());
                    }
                });

        log.info("✅ Message edited: {}", saved.getId());
        return messageMapper.toDto(saved);
    }

    /**
     * ✅ Delete message (parents blocked)
     */
    @Transactional
    public void deleteMessage(String messageId, String userId, String userRole) {
        log.info("Deleting message {} by {} ({})", messageId, userId, userRole);

        if ("PARENT".equals(userRole)) {
            throw new ParentAccessDeniedException("Parents cannot delete messages");
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Only sender can delete the message");
        }

        if (!message.canBeDeleted()) {
            throw new IllegalStateException("Message cannot be deleted");
        }

        message.softDelete();
        messageRepository.save(message);
        log.info("✅ Message soft-deleted: {}", messageId);
    }

    /**
     * ✅ Get unread count (parents always get 0)
     */
    public long getUnreadCount(String conversationId, String userId, String userRole) {
        if ("PARENT".equals(userRole)) {
            return 0L;
        }
        return messageRepository.countByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
                conversationId, MessageStatus.SENT, userId);
    }

    /**
     * Delete expired messages (scheduled job)
     */
    @Transactional
    public void deleteExpiredMessages() {
        log.info("Deleting expired messages");
        List<Message> expiredMessages = messageRepository.findExpiredMessages(LocalDateTime.now());
        messageRepository.deleteAll(expiredMessages);
        log.info("✅ Deleted {} expired messages", expiredMessages.size());
    }

    /**
     * Get latest message for conversation list
     */
    public MessageDto getLatestMessage(String conversationId) {
        return messageRepository.findTopByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId)
                .map(messageMapper::toDto)
                .orElse(null);
    }

    // MessageService.java
    public void validateParentAccess(String parentId, String conversationId) {
        log.info("Validating parent {} access to conversation {}", parentId, conversationId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> {
                    log.warn("Conversation not found: {}", conversationId);
                    return new IllegalArgumentException("Conversation not found: " + conversationId);
                });

        List<String> participants = conversation.getParticipantIds();
        log.info("Conversation {} participants: {}", conversationId, participants);

        // 1) Parent is direct participant (PARENT_DIRECT) → allow
        if (participants.contains(parentId)) {
            log.info("✅ Parent {} is direct participant in conv {}", parentId, conversationId);
            return;
        }

        // 2) Parent authorized if any of their children is a participant
        List<String> childIds = parentServiceClient.getChildStudentIds(parentId);
        log.info("Parent {} has children {}", parentId, childIds);

        boolean anyChild = childIds.stream().anyMatch(participants::contains);

        if (anyChild) {
            log.info("✅ Parent {} authorized via child participant in conv {}", parentId, conversationId);
            return;
        }

        log.warn("🚫 Parent {} NOT authorized for conv {}", parentId, conversationId);
        throw new ParentAccessDeniedException("Parent not linked to any participant in this conversation");
    }

    /**
     * Mark message as delivered
     */
    @Transactional
    public void markAsDelivered(String messageId) {
        messageRepository.findById(messageId)
                .ifPresent(message -> {
                    if (message.getStatus() == MessageStatus.SENT) {
                        message.setStatus(MessageStatus.DELIVERED);
                        message.setDeliveredAt(LocalDateTime.now());
                        messageRepository.save(message);
                        messageEventPublisher.publishMessageDelivered(message);
                        log.debug("✅ Message {} marked as delivered", messageId);
                    }
                });
    }
}