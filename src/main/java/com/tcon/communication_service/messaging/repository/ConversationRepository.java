package com.tcon.communication_service.messaging.repository;

import com.tcon.communication_service.messaging.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Conversation Repository
 * MongoDB repository for Conversation entity
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /**
     * Find conversation by participant IDs
     * Checks if participantIds array contains ALL specified IDs
     */
    @Query("{ 'participantIds': { $all: ?0 } }")
    Optional<Conversation> findByParticipantIds(List<String> participantIds);

    /**
     * Check if conversation exists with given participant IDs
     */
    @Query(value = "{ 'participantIds': { $all: ?0 } }", exists = true)
    boolean existsByParticipantIds(List<String> participantIds);

    /**
     * Find all conversations for a user with pagination
     */
    @Query(value = "{ 'participantIds': ?0 }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByParticipantIdsContaining(String userId, Pageable pageable);

    /**
     * Find all conversations for a user (List version)
     */
    @Query(value = "{ 'participantIds': ?0 }", sort = "{ 'lastMessageAt': -1 }")
    List<Conversation> findByParticipantIdsContaining(String userId);

    /**
     * Find active conversations (with recent messages)
     */
    @Query(value = "{ 'participantIds': ?0, 'lastMessageAt': { $exists: true } }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findActiveConversations(String userId, Pageable pageable);

    /**
     * Count unread conversations for a user
     */
    @Query(value = "{ 'participantIds': ?0, 'unreadCounts.?0': { $gt: 0 } }", count = true)
    long countUnreadConversations(String userId);

    /**
     * Find conversations by type
     */
    @Query(value = "{ 'type': ?0, 'participantIds': ?1 }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByTypeAndParticipantIdsContaining(String type, String userId, Pageable pageable);

    /**
     * Delete conversations by participant ID
     */
    @Query(value = "{ 'participantIds': ?0 }", delete = true)
    void deleteByParticipantId(String userId);
}
