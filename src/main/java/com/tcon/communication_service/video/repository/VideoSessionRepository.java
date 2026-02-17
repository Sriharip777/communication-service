package com.tcon.communication_service.video.repository;

import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.VideoSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Video Session Repository
 * Data access layer for video sessions
 */
@Repository
public interface VideoSessionRepository extends MongoRepository<VideoSession, String> {

    Optional<VideoSession> findByClassSessionId(String classSessionId);

    @Query("{'metadata.courseId': ?0}")
    List<VideoSession> findByCourseId(String courseId);

    Optional<VideoSession> findByHundredMsRoomId(String roomId);

    Page<VideoSession> findByTeacherIdAndStatus(String teacherId, SessionStatus status, Pageable pageable);

    Page<VideoSession> findByStudentIdAndStatus(String studentId, SessionStatus status, Pageable pageable);

    // ❌ OLD: Paginated (limited to 20 by default)
    Page<VideoSession> findByTeacherId(String teacherId, Pageable pageable);
    Page<VideoSession> findByStudentId(String studentId, Pageable pageable);

    // ✅ NEW: Fetch ALL sessions without pagination, sorted by newest first
    List<VideoSession> findByTeacherIdOrderByScheduledStartTimeDesc(String teacherId);
    List<VideoSession> findByStudentIdOrderByScheduledStartTimeDesc(String studentId);

    List<VideoSession> findByStatusAndScheduledStartTimeBefore(SessionStatus status, LocalDateTime time);

    @Query("{ 'status': ?0, 'scheduledStartTime': { $gte: ?1, $lte: ?2 } }")
    List<VideoSession> findByStatusAndScheduledStartTimeBetween(
            SessionStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("{ 'teacherId': ?0, 'status': { $in: ?1 }, 'scheduledStartTime': { $gte: ?2, $lte: ?3 } }")
    List<VideoSession> findTeacherSessionsInDateRange(
            String teacherId,
            List<SessionStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );

    long countByTeacherIdAndStatus(String teacherId, SessionStatus status);

    long countByStudentIdAndStatus(String studentId, SessionStatus status);

    boolean existsByClassSessionId(String classSessionId);

    @Query("{ 'status': 'IN_PROGRESS', 'actualStartTime': { $lte: ?0 } }")
    List<VideoSession> findStuckInProgressSessions(LocalDateTime threshold);

    // ✅ NEW: Find active/upcoming sessions only
    @Query("{ 'teacherId': ?0, 'status': { $in: ['SCHEDULED', 'IN_PROGRESS'] } }")
    List<VideoSession> findActiveSessionsByTeacherId(String teacherId);

    @Query("{ 'studentId': ?0, 'status': { $in: ['SCHEDULED', 'IN_PROGRESS'] } }")
    List<VideoSession> findActiveSessionsByStudentId(String studentId);
}