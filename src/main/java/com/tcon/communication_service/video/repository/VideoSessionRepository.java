package com.tcon.communication_service.video.repository;

import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.VideoSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoSessionRepository extends MongoRepository<VideoSession, String> {

    List<VideoSession> findByStatus(SessionStatus status);

    // ── By classSessionId ─────────────────────────────────────────────────────
    Optional<VideoSession> findByClassSessionId(String classSessionId);
    boolean existsByClassSessionId(String classSessionId);

    // ── By bookingId ──────────────────────────────────────────────────────────
    Optional<VideoSession> findByBookingId(String bookingId);
    boolean existsByBookingId(String bookingId);

    // ── By roomId ─────────────────────────────────────────────────────────────
    Optional<VideoSession> findByHundredMsRoomId(String roomId);

    // ── By courseId (in metadata) ─────────────────────────────────────────────
    @Query("{'metadata.courseId': ?0}")
    List<VideoSession> findByCourseId(String courseId);

    // ── Teacher queries ───────────────────────────────────────────────────────
    Page<VideoSession> findByTeacherIdAndStatus(
            String teacherId, SessionStatus status, Pageable pageable);

    List<VideoSession> findByTeacherIdOrderByScheduledStartTimeDesc(String teacherId);

    @Query("{ 'teacherId': ?0, 'status': { $in: ['SCHEDULED', 'IN_PROGRESS'] } }")
    List<VideoSession> findActiveSessionsByTeacherId(String teacherId);

    long countByTeacherIdAndStatus(String teacherId, SessionStatus status);

    @Query("{ 'teacherId': ?0, 'status': { $in: ?1 }, " +
            "'scheduledStartTime': { $gte: ?2, $lte: ?3 } }")
    List<VideoSession> findTeacherSessionsInDateRange(
            String teacherId,
            List<SessionStatus> statuses,
            Instant start,
            Instant end
    );

    // ── Student queries ───────────────────────────────────────────────────────
    Page<VideoSession> findByStudentIdAndStatus(
            String studentId, SessionStatus status, Pageable pageable);

    List<VideoSession> findByStudentIdOrderByScheduledStartTimeDesc(String studentId);

    @Query("{ 'studentId': ?0, 'status': { $in: ['SCHEDULED', 'IN_PROGRESS'] } }")
    List<VideoSession> findActiveSessionsByStudentId(String studentId);

    long countByStudentIdAndStatus(String studentId, SessionStatus status);

    List<VideoSession> findByStatusAndScheduledStartTimeBefore(
            SessionStatus status,
            Instant dateTime
    );

    @Query("{ 'status': ?0, 'scheduledStartTime': { $gte: ?1, $lte: ?2 } }")
    List<VideoSession> findByStatusAndScheduledStartTimeBetween(
            SessionStatus status,
            Instant start,
            Instant end
    );

    // ── Stuck sessions ────────────────────────────────────────────────────────
    @Query("{ 'status': 'IN_PROGRESS', 'actualStartTime': { $lte: ?0 } }")
    List<VideoSession> findStuckInProgressSessions(Instant threshold);
}