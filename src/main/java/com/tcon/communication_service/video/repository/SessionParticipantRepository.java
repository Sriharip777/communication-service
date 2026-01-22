package com.tcon.communication_service.video.repository;

import com.tcon.communication_service.video.entity.VideoSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom queries for session participants
 * Using VideoSession repository with participant-specific queries
 */
@Repository
public interface SessionParticipantRepository extends MongoRepository<VideoSession, String> {

    @Query("{ 'participants.userId': ?0, 'participants.leftAt': null }")
    List<VideoSession> findActiveSessionsByUserId(String userId);

    @Query("{ 'participants': { $elemMatch: { 'userId': ?0, 'role': ?1 } } }")
    List<VideoSession> findSessionsByUserIdAndRole(String userId, String role);
}
