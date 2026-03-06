package com.tcon.communication_service.messaging.repository;

import com.tcon.communication_service.messaging.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // Find by exact participant list (sorted)
    @Query("{ 'participantIds': { $all: ?0 } }")
    Optional<Conversation> findByParticipantIds(List<String> participantIds);

    // Check existence by participant list
    @Query(value = "{ 'participantIds': { $all: ?0 } }", exists = true)
    boolean existsByParticipantIds(List<String> participantIds);

    // All conversations for a user (any role)
    @Query(value = "{ 'participantIds': ?0 }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByParticipantIdsContaining(String userId, Pageable pageable);

    // Used by ParentTeacherDirectoryService
    List<Conversation> findByParticipantIdsIn(List<String> participantIds);

    // Active conversations (with messages)
    @Query(value = "{ 'participantIds': ?0, 'lastMessageAt': { $exists: true } }",
            sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findActiveConversations(String userId, Pageable pageable);

    // Unread conversations count
    @Query(value = "{ 'participantIds': ?0, 'unreadCounts.?0': { $gt: 0 } }", count = true)
    long countUnreadConversations(String userId);

    // By type + participant
    @Query(value = "{ 'type': ?0, 'participantIds': ?1 }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByTypeAndParticipantIdsContaining(String type, String userId, Pageable pageable);

    // ✅ Parent observer: conversations involving any child
    @Query(value = "{ 'participantIds': { $in: ?0 } }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByAnyParticipantInChildren(List<String> childIds, Pageable pageable);

    // Delete by participant
    @Query(value = "{ 'participantIds': ?0 }", delete = true)
    void deleteByParticipantId(String userId);

    // ✅ Find by exact participants + type (for getOrCreate)
    @Query("{ 'participantIds': { $all: ?0 }, 'type': ?1 }")
    Optional<Conversation> findByParticipantIdsAndType(List<String> participantIds, String type);

    // ✅ Parent-direct: by single participant + type
    @Query(value = "{ 'participantIds': ?0, 'type': ?1 }", sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByParticipantAndType(String userId, String type, Pageable pageable);

    // ✅ Teacher: DIRECT + PARENT_DIRECT in one query
    @Query(value = "{ 'participantIds': ?0, 'type': { $in: ?1 } }",
            sort = "{ 'lastMessageAt': -1 }")
    Page<Conversation> findByParticipantAndTypeIn(String userId, List<String> types, Pageable pageable);
}