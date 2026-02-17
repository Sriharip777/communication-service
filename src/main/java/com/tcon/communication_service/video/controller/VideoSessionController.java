package com.tcon.communication_service.video.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcon.communication_service.video.dto.RoomCreateRequest;
import com.tcon.communication_service.video.dto.RoomJoinResponse;
import com.tcon.communication_service.video.dto.VideoSessionDto;
import com.tcon.communication_service.video.entity.ParticipantRole;
import com.tcon.communication_service.video.service.VideoSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Video Session Controller
 * REST API endpoints for video session management
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoSessionController {

    private final VideoSessionService videoSessionService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new video session room
     */
    @PostMapping("/sessions")
    public ResponseEntity<VideoSessionDto> createRoom(
            @Valid @RequestBody RoomCreateRequest request) {
        log.info("Creating video session room for class: {}", request.getClassSessionId());
        VideoSessionDto session = videoSessionService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * Join a video session
     */
    @PostMapping("/sessions/{sessionId}/join")
    public ResponseEntity<RoomJoinResponse> joinSession(
            @PathVariable String sessionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam ParticipantRole role) {
        log.info("User {} joining session {}", userId, sessionId);
        RoomJoinResponse response = videoSessionService.joinSession(sessionId, userId, role);
        return ResponseEntity.ok(response);
    }

    /**
     * End a video session
     */
    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<VideoSessionDto> endSession(
            @PathVariable String sessionId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Ending session {} by user {}", sessionId, userId);
        VideoSessionDto session = videoSessionService.endSession(sessionId, userId);
        return ResponseEntity.ok(session);
    }

    /**
     * Get session by class/booking ID
     */
    @GetMapping("/sessions/class/{classSessionId}")
    public ResponseEntity<VideoSessionDto> getSessionByClassId(
            @PathVariable String classSessionId,
            @RequestHeader("X-User-Id") String userId) {

        log.info("📥 Getting video session for class: {} by user: {}", classSessionId, userId);

        try {
            VideoSessionDto session = videoSessionService.getSessionByClassId(classSessionId);

            log.info("✅ DTO created: id={}, classId={}, roomId={}",
                    session.getId(),
                    session.getClassSessionId(),
                    session.getHundredMsRoomId());

            // ✅ Test JSON serialization
            try {
                String json = objectMapper.writeValueAsString(session);
                log.info("✅ JSON serialized successfully, length: {} bytes", json.length());
                log.debug("📄 JSON: {}", json);
            } catch (Exception e) {
                log.error("❌ JSON SERIALIZATION FAILED: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to serialize session to JSON", e);
            }

            return ResponseEntity.ok(session);

        } catch (IllegalArgumentException e) {
            log.error("❌ Session not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ ERROR getting session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get session by ID
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<VideoSessionDto> getSession(@PathVariable String sessionId) {
        VideoSessionDto session = videoSessionService.getSessionById(sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * ✅ FIXED: Get ALL teacher sessions (no pagination limit)
     */
    @GetMapping("/sessions/teacher/{teacherId}")
    public ResponseEntity<List<VideoSessionDto>> getTeacherSessions(
            @PathVariable String teacherId) {
        log.info("📥 API: Get ALL sessions for teacher: {}", teacherId);

        List<VideoSessionDto> sessions = videoSessionService.getTeacherSessions(teacherId);

        log.info("✅ Returning {} total sessions for teacher {}", sessions.size(), teacherId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * ✅ FIXED: Get ALL student sessions (no pagination limit)
     */
    @GetMapping("/sessions/student/{studentId}")
    public ResponseEntity<List<VideoSessionDto>> getStudentSessions(
            @PathVariable String studentId) {
        log.info("📥 API: Get ALL sessions for student: {}", studentId);

        List<VideoSessionDto> sessions = videoSessionService.getStudentSessions(studentId);

        log.info("✅ Returning {} total sessions for student {}", sessions.size(), studentId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get active sessions
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<List<VideoSessionDto>> getActiveSessions() {
        List<VideoSessionDto> sessions = videoSessionService.getActiveSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Start recording
     */
    @PostMapping("/sessions/{sessionId}/recording/start")
    public ResponseEntity<Void> startRecording(@PathVariable String sessionId) {
        log.info("Starting recording for session: {}", sessionId);
        videoSessionService.startRecording(sessionId);
        return ResponseEntity.ok().build();
    }
}