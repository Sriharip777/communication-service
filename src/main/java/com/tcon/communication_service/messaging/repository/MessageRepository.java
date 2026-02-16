package com.tcon.communication_service.messaging.repository;

import com.tcon.communication_service.messaging.entity.Message;
import com.tcon.communication_service.messaging.entity.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Message Repository
 * Data access layer for messages
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    /**
     * Find messages by conversation with pagination (descending order)
     */
    Page<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String conversationId,
            Pageable pageable
    );

    /**
     * ✅ NEW: Find latest single message in conversation
     * Used for getting the most recent message
     */
    Optional<Message> findTopByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String conversationId
    );

    /**
     * Find top 50 messages in conversation (for quick loading)
     */
    List<Message> findTop50ByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String conversationId
    );

    /**
     * ✅ Find unread messages by status and receiver
     * Used for marking conversations as read
     */
    List<Message> findByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
            String conversationId,
            MessageStatus status,
            String receiverId
    );

    /**
     * ✅ Count unread messages for a user in a conversation
     * Used for unread badge count
     */
    long countByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
            String conversationId,
            MessageStatus status,
            String receiverId
    );

    /**
     * ✅ NEW: Find messages by receiver with null readAt (alternative unread check)
     * Used when checking messages by readAt field instead of status
     */
    List<Message> findByConversationIdAndReceiverIdAndReadAtIsNull(
            String conversationId,
            String receiverId
    );

    /**
     * ✅ NEW: Count unread messages by readAt field
     */
    long countByConversationIdAndReceiverIdAndReadAtIsNull(
            String conversationId,
            String receiverId
    );

    /**
     * Find recent messages since a specific time
     */
    @Query("{ 'conversationId': ?0, 'createdAt': { $gte: ?1 }, 'isDeleted': false }")
    List<Message> findRecentMessages(String conversationId, LocalDateTime since);

    /**
     * Find messages between two users (for direct conversations)
     */
    @Query("{ 'senderId': ?0, 'receiverId': ?1, 'isDeleted': false }")
    Page<Message> findBySenderAndReceiver(String senderId, String receiverId, Pageable pageable);

    /**
     * Find expired messages (for cleanup job)
     */
    @Query("{ 'expiresAt': { $lte: ?0 } }")
    List<Message> findExpiredMessages(LocalDateTime now);

    /**
     * Count all messages in conversation (including deleted)
     */
    long countByConversationIdAndIsDeletedFalse(String conversationId);

    /**
     * ✅ NEW: Find all messages by sender
     * Used for user message history
     */
    Page<Message> findBySenderIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String senderId,
            Pageable pageable
    );

    /**
     * ✅ NEW: Find all messages by receiver
     */
    Page<Message> findByReceiverIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String receiverId,
            Pageable pageable
    );

    /**
     * ✅ NEW: Search messages by content
     * Used for message search functionality
     */
    @Query("{ 'conversationId': ?0, 'content': { $regex: ?1, $options: 'i' }, 'isDeleted': false }")
    List<Message> searchByContent(String conversationId, String searchText);

    /**
     * ✅ NEW: Find messages by type (TEXT, IMAGE, FILE, etc.)
     */
    List<Message> findByConversationIdAndTypeAndIsDeletedFalseOrderByCreatedAtDesc(
            String conversationId,
            String type
    );

    /**
     * ✅ NEW: Find edited messages
     */
    @Query("{ 'conversationId': ?0, 'isEdited': true, 'isDeleted': false }")
    List<Message> findEditedMessages(String conversationId);

    /**
     * ✅ NEW: Delete all messages in a conversation (for cleanup)
     */
    void deleteByConversationId(String conversationId);

    /**
     * ✅ NEW: Count total messages sent by a user
     */
    long countBySenderIdAndIsDeletedFalse(String senderId);

    /**
     * ✅ NEW: Find messages with attachments
     */
    @Query("{ 'conversationId': ?0, 'fileUrl': { $exists: true, $ne: null }, 'isDeleted': false }")
    List<Message> findMessagesWithAttachments(String conversationId);

    /**
     * ✅ NEW: Find reply messages (messages that are replies to another message)
     */
    @Query("{ 'conversationId': ?0, 'replyToMessageId': { $exists: true, $ne: null }, 'isDeleted': false }")
    List<Message> findReplyMessages(String conversationId);
}