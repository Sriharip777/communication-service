package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.dto.MessageSendRequest;
import com.tcon.communication_service.messaging.entity.*;
import com.tcon.communication_service.messaging.event.MessageEventPublisher;  // ← Fixed import
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

/**
 * Message Service
 * Business logic for messaging functionality
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final MessageMapper messageMapper;
    private final MessageEventPublisher messageEventPublisher;  // ← Will inject from .event package

    /**
     * Send a new message
     */
    @Transactional
    public MessageDto sendMessage(String senderId, MessageSendRequest request) {
        log.info("Sending message from {} to {}", senderId, request.getReceiverId());

        // Get or create conversation
        Conversation conversation = conversationService.getOrCreateConversation(
                senderId,
                request.getReceiverId()
        );

        // Build message
        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(senderId)
                .receiverId(request.getReceiverId())
                .content(request.getContent())
                .type(request.getType())
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

        // Set reply content if replying to a message
        if (request.getReplyToMessageId() != null) {
            messageRepository.findById(request.getReplyToMessageId())
                    .ifPresent(replyTo -> message.setReplyToContent(replyTo.getContent()));
        }

        Message saved = messageRepository.save(message);

        // Update conversation
        conversation.updateLastMessage(saved.getId(), saved.getContent(), senderId);
        conversationRepository.save(conversation);

        // Publish message event
        messageEventPublisher.publishMessageSent(saved);

        log.info("Message sent successfully: {}", saved.getId());
        return messageMapper.toDto(saved);
    }

    /**
     * Get messages in a conversation
     */
    public Page<MessageDto> getConversationMessages(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        conversationId,
                        pageable)
                .map(messageMapper::toDto);
    }

    /**
     * Mark message as read
     */
    @Transactional
    public MessageDto markAsRead(String messageId, String userId) {
        log.info("Marking message {} as read by {}", messageId, userId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getReceiverId().equals(userId)) {
            throw new IllegalArgumentException("Only receiver can mark message as read");
        }

        if (message.getStatus() != MessageStatus.READ) {
            message.markAsRead();
            Message saved = messageRepository.save(message);

            // Reset unread count in conversation
            conversationRepository.findById(message.getConversationId())
                    .ifPresent(conv -> {
                        conv.resetUnreadCount(userId);
                        conversationRepository.save(conv);
                    });

            // Publish event
            messageEventPublisher.publishMessageRead(saved);

            return messageMapper.toDto(saved);
        }

        return messageMapper.toDto(message);
    }

    /**
     * Mark all messages in conversation as read
     */
    @Transactional
    public void markConversationAsRead(String conversationId, String userId) {
        log.info("Marking all messages in conversation {} as read by {}", conversationId, userId);

        List<Message> unreadMessages = messageRepository
                .findByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
                        conversationId,
                        MessageStatus.SENT,
                        userId
                );

        unreadMessages.forEach(msg -> {
            msg.markAsRead();
            messageEventPublisher.publishMessageRead(msg);
        });

        messageRepository.saveAll(unreadMessages);

        // Reset unread count
        conversationRepository.findById(conversationId)
                .ifPresent(conv -> {
                    conv.resetUnreadCount(userId);
                    conversationRepository.save(conv);
                });
    }

    /**
     * Edit a message
     */
    @Transactional
    public MessageDto editMessage(String messageId, String userId, String newContent) {
        log.info("Editing message {} by user {}", messageId, userId);

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

        log.info("Message edited successfully: {}", saved.getId());
        return messageMapper.toDto(saved);
    }

    /**
     * Delete a message
     */
    @Transactional
    public void deleteMessage(String messageId, String userId) {
        log.info("Deleting message {} by user {}", messageId, userId);

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

        log.info("Message deleted successfully: {}", messageId);
    }

    /**
     * Get unread message count
     */
    public long getUnreadCount(String conversationId, String userId) {
        return messageRepository.countByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
                conversationId,
                MessageStatus.SENT,
                userId
        );
    }

    /**
     * Delete expired messages (scheduled job)
     */
    @Transactional
    public void deleteExpiredMessages() {
        log.info("Deleting expired messages");

        List<Message> expiredMessages = messageRepository.findExpiredMessages(LocalDateTime.now());
        messageRepository.deleteAll(expiredMessages);

        log.info("Deleted {} expired messages", expiredMessages.size());
    }
}
