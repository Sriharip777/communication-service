package com.tcon.communication_service.video.controller;

import com.tcon.communication_service.video.dto.RoomCreateRequest;
import com.tcon.communication_service.video.dto.RoomJoinResponse;
import com.tcon.communication_service.video.dto.VideoSessionDto;
import com.tcon.communication_service.video.entity.ParticipantRole;
import com.tcon.communication_service.video.service.VideoSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@CrossOrigin(origins = "*")
public class VideoSessionController {

    private final VideoSessionService videoSessionService;

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
     * Get session by ID
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<VideoSessionDto> getSession(@PathVariable String sessionId) {
        VideoSessionDto session = videoSessionService.getSessionById(sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Get teacher sessions
     */
    @GetMapping("/sessions/teacher/{teacherId}")
    public ResponseEntity<Page<VideoSessionDto>> getTeacherSessions(
            @PathVariable String teacherId,
            Pageable pageable) {
        Page<VideoSessionDto> sessions = videoSessionService.getTeacherSessions(teacherId, pageable);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get student sessions
     */
    @GetMapping("/sessions/student/{studentId}")
    public ResponseEntity<Page<VideoSessionDto>> getStudentSessions(
            @PathVariable String studentId,
            Pageable pageable) {
        Page<VideoSessionDto> sessions = videoSessionService.getStudentSessions(studentId, pageable);
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
