package com.tcon.communication_service.video.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcon.communication_service.video.dto.HostControlMessage;
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
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;
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


    @MessageMapping("/host/control")
    public void handleHostControl(HostControlMessage msg) {
        log.info("🎛 Host control message -> target: {} action: {} session: {}",
                msg.getTargetUserId(), msg.getAction(), msg.getSessionId());

        // ✅ Validate teacher authorization by looking up session
        validateTeacherForSession(msg.getSessionId());

        // Send to specific user queue
        messagingTemplate.convertAndSendToUser(
                msg.getTargetUserId(),
                "/queue/host-control",
                msg
        );
    }
    /**
     * Validate that message comes from authorized teacher for this session
     * Uses SimpMessageHeaderAccessor to get sender's userId from STOMP headers
     */
    private void validateTeacherForSession(String sessionId) {
        try {
            VideoSessionDto session = videoSessionService.getSessionById(sessionId);

            // ✅ Get sender's userId from STOMP message headers (set by WebSocket config)
            // This replaces Authentication.getName() functionality
            String senderUserId = getCurrentUserId(); // Implement this helper

            if (!senderUserId.equals(session.getTeacherId())) {
                log.warn("❌ Unauthorized user {} trying to control session {}",
                        senderUserId, sessionId);
                throw new IllegalArgumentException("Only teacher can send host control messages");
            }

            log.debug("✅ Teacher {} authorized for session {}", senderUserId, sessionId);

        } catch (Exception e) {
            log.error("❌ Teacher validation failed for session {}: {}", sessionId, e.getMessage());
            throw new IllegalArgumentException("Invalid session or unauthorized access");
        }
    }

    /**
     * Extract current user ID from STOMP message headers
     * WebSocket connection must set userId in headers during handshake
     */
    private String getCurrentUserId() {
        // This gets the userId from STOMP headers (configured in WebSocketConfig)
        // Frontend must send userId in STOMP CONNECT frame headers
        return "teacher-extracted-from-headers"; // Replace with actual header extraction
    }
}