package com.tcon.communication_service.whiteboard.repository;

import com.tcon.communication_service.whiteboard.entity.WhiteboardSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Whiteboard Snapshot Repository
 * Data access layer for whiteboard snapshots
 */
@Repository
public interface WhiteboardSnapshotRepository extends MongoRepository<WhiteboardSnapshot, String> {

    /**
     * Get all snapshots for a specific class session, ordered by snapshot number
     */
    List<WhiteboardSnapshot> findByClassSessionIdOrderBySnapshotNumberAsc(String classSessionId);

    /**
     * Get all snapshots for a course (GROUP classes), ordered by creation date
     */
    List<WhiteboardSnapshot> findByCourseIdOrderByCreatedAtDesc(String courseId);

    /**
     * Get snapshots accessible by a specific student
     */
    List<WhiteboardSnapshot> findByAuthorizedStudentIdsContaining(String studentId);

    /**
     * Get course snapshots accessible by a student (for GROUP classes)
     */
    List<WhiteboardSnapshot> findByCourseIdAndAuthorizedStudentIdsContaining(
            String courseId, String studentId);

    /**
     * Count snapshots for a class session
     */
    Long countByClassSessionId(String classSessionId);

    /**
     * Find snapshots by teacher
     */
    List<WhiteboardSnapshot> findByTeacherIdOrderByCreatedAtDesc(String teacherId);

    /**
     * Custom query: Find recent snapshots for a student across all their sessions
     */
    @Query("{ 'authorizedStudentIds': ?0 }")
    List<WhiteboardSnapshot> findRecentSnapshotsForStudent(String studentId);
}