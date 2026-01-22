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

/**
 * Message Repository
 * Data access layer for messages
 */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String conversationId,
            Pageable pageable
    );

    List<Message> findByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
            String conversationId,
            MessageStatus status,
            String receiverId
    );

    long countByConversationIdAndStatusAndReceiverIdAndIsDeletedFalse(
            String conversationId,
            MessageStatus status,
            String receiverId
    );

    @Query("{ 'conversationId': ?0, 'createdAt': { $gte: ?1 }, 'isDeleted': false }")
    List<Message> findRecentMessages(String conversationId, LocalDateTime since);

    @Query("{ 'senderId': ?0, 'receiverId': ?1, 'isDeleted': false }")
    Page<Message> findBySenderAndReceiver(String senderId, String receiverId, Pageable pageable);

    @Query("{ 'expiresAt': { $lte: ?0 } }")
    List<Message> findExpiredMessages(LocalDateTime now);

    long countByConversationIdAndIsDeletedFalse(String conversationId);

    List<Message> findTop50ByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(String conversationId);
}
