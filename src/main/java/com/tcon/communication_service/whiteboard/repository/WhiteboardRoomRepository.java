package com.tcon.communication_service.whiteboard.repository;

import com.tcon.communication_service.video.entity.SessionType;
import com.tcon.communication_service.whiteboard.entity.WhiteboardRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Whiteboard Room Repository
 * Data access layer for whiteboard rooms
 */
@Repository
public interface WhiteboardRoomRepository extends MongoRepository<WhiteboardRoom, String> {

    /**
     * Find whiteboard room by class session ID (primary lookup)
     */
    Optional<WhiteboardRoom> findByClassSessionId(String classSessionId);

    List<WhiteboardRoom> findByIsActiveTrueAndLastDisconnectTimeBefore(LocalDateTime threshold);

    /**
     * Find by Agora whiteboard UUID
     */
    Optional<WhiteboardRoom> findByAgoraWhiteboardUuid(String uuid);

    /**
     * Find all whiteboard rooms for a course (GROUP classes)
     */
    List<WhiteboardRoom> findByCourseId(String courseId);

    /**
     * Find active rooms for a teacher
     */
    List<WhiteboardRoom> findByTeacherIdAndIsActive(String teacherId, Boolean isActive);

    /**
     * Find rooms by session type
     */
    List<WhiteboardRoom> findBySessionType(SessionType sessionType);

    /**
     * Find active rooms for a specific student (SOLO/DEMO sessions)
     */
    @Query("{ 'studentId': ?0, 'isActive': true }")
    List<WhiteboardRoom> findActiveRoomsForStudent(String studentId);

    /**
     * Find active GROUP rooms where student is enrolled
     */
    @Query("{ 'enrolledStudentIds': ?0, 'isActive': true, 'sessionType': 'GROUP' }")
    List<WhiteboardRoom> findActiveGroupRoomsForStudent(String studentId);

    /**
     * Delete room by class session ID
     */
    void deleteByClassSessionId(String classSessionId);

    /**
     * Check if whiteboard exists for a class session
     */
    boolean existsByClassSessionId(String classSessionId);

    /**
     * Find expired rooms (for cleanup)
     */
    @Query("{ 'isActive': true, 'expiresAt': { $lt: ?0 } }")
    List<WhiteboardRoom> findExpiredRooms(LocalDateTime now);
}