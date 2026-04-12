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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoSessionController {

    private final VideoSessionService videoSessionService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // ==================== CREATE ROOM ====================

    @PostMapping("/sessions")
    public ResponseEntity<VideoSessionDto> createRoom(
            @Valid @RequestBody RoomCreateRequest request) {
        log.info("Creating video session room for class: {}", request.getClassSessionId());
        VideoSessionDto session = videoSessionService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    // ==================== JOIN SESSION ====================

    @PostMapping("/sessions/{sessionId}/join")
    public ResponseEntity<RoomJoinResponse> joinSession(
            @PathVariable String sessionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam ParticipantRole role) {
        log.info("🚪 User {} joining session {} as {}", userId, sessionId, role);
        RoomJoinResponse response = videoSessionService.joinSession(sessionId, userId, role);
        return ResponseEntity.ok(response);
    }

    // ==================== END SESSION ====================

    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<VideoSessionDto> endSession(
            @PathVariable String sessionId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("🛑 Ending session {} by user {}", sessionId, userId);
        VideoSessionDto session = videoSessionService.endSession(sessionId, userId);
        return ResponseEntity.ok(session);
    }

    // ==================== GET BY CLASS ID ====================

    @GetMapping("/sessions/class/{classSessionId}")
    public ResponseEntity<VideoSessionDto> getSessionByClassId(
            @PathVariable String classSessionId,
            @RequestHeader("X-User-Id") String userId) {

        log.info("📥 Getting video session for class={} by user={}", classSessionId, userId);

        try {
            VideoSessionDto session = videoSessionService.getSessionByClassId(classSessionId);

            log.info("✅ Session found: id={}, classId={}, roomId={}",
                    session.getId(),
                    session.getClassSessionId(),
                    session.getHundredMsRoomId());

            // Validate JSON serialization
            try {
                String json = objectMapper.writeValueAsString(session);
                log.info("✅ JSON serialized: {} bytes", json.length());
                log.debug("📄 JSON: {}", json);
            } catch (Exception e) {
                log.error("❌ JSON SERIALIZATION FAILED: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to serialize session to JSON", e);
            }

            return ResponseEntity.ok(session);

        } catch (IllegalArgumentException e) {
            log.error("❌ Session not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.notFound().build();
            }
            throw e;
        } catch (Exception e) {
            log.error("❌ ERROR getting session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== GET BY BOOKING ID ✅ NEW ====================

    /**
     * Get video session by booking ID
     * Called by student/teacher "Join Class" button
     * GET /api/video/sessions/booking/{bookingId}
     */
    @GetMapping("/sessions/booking/{bookingId}")
    public ResponseEntity<VideoSessionDto> getSessionByBookingId(
            @PathVariable String bookingId,
            @RequestHeader("X-User-Id") String userId) {

        log.info("📥 Getting session for bookingId={} by userId={}", bookingId, userId);

        try {
            VideoSessionDto session = videoSessionService.getSessionByBookingId(bookingId);

            log.info("✅ Session found: id={}, canJoin={}, status={}",
                    session.getId(), session.getCanJoin(), session.getStatus());

            return ResponseEntity.ok(session);

        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("⚠️ No session found for bookingId={}", bookingId);
                return ResponseEntity.notFound().build();
            }
            throw e;
        } catch (Exception e) {
            log.error("❌ Error getting session for bookingId={}: {}",
                    bookingId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== GET BY SESSION ID ====================

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<VideoSessionDto> getSession(
            @PathVariable String sessionId) {
        VideoSessionDto session = videoSessionService.getSessionById(sessionId);
        return ResponseEntity.ok(session);
    }

    // ==================== GET TEACHER SESSIONS ====================

    @GetMapping("/sessions/teacher/{teacherId}")
    public ResponseEntity<List<VideoSessionDto>> getTeacherSessions(
            @PathVariable String teacherId) {
        log.info("📥 Get ALL sessions for teacher: {}", teacherId);
        List<VideoSessionDto> sessions = videoSessionService.getTeacherSessions(teacherId);
        log.info("✅ Returning {} sessions for teacher {}", sessions.size(), teacherId);
        return ResponseEntity.ok(sessions);
    }

    // ==================== GET STUDENT SESSIONS ====================

    @GetMapping("/sessions/student/{studentId}")
    public ResponseEntity<List<VideoSessionDto>> getStudentSessions(
            @PathVariable String studentId) {
        log.info("📥 Get ALL sessions for student: {}", studentId);
        List<VideoSessionDto> sessions = videoSessionService.getStudentSessions(studentId);
        log.info("✅ Returning {} sessions for student {}", sessions.size(), studentId);
        return ResponseEntity.ok(sessions);
    }

    // ==================== GET ACTIVE SESSIONS ====================

    @GetMapping("/sessions/active")
    public ResponseEntity<List<VideoSessionDto>> getActiveSessions() {
        log.info("📥 Getting active sessions");
        List<VideoSessionDto> sessions = videoSessionService.getActiveSessions();
        log.info("✅ Returning {} active sessions", sessions.size());
        return ResponseEntity.ok(sessions);
    }

    // ==================== START RECORDING ====================

    @PostMapping("/sessions/{sessionId}/recording/start")
    public ResponseEntity<Void> startRecording(@PathVariable String sessionId) {
        log.info("🎙️ Starting recording for session: {}", sessionId);
        videoSessionService.startRecording(sessionId);
        return ResponseEntity.ok().build();
    }

    // ==================== WEBSOCKET HOST CONTROL ====================

    @MessageMapping("/host/control")
    public void handleHostControl(HostControlMessage msg) {
        log.info("🎛 Host control: target={}, action={}, session={}",
                msg.getTargetUserId(), msg.getAction(), msg.getSessionId());

        validateTeacherForSession(msg.getSessionId());

        messagingTemplate.convertAndSendToUser(
                msg.getTargetUserId(),
                "/queue/host-control",
                msg
        );
    }

    // ==================== PRIVATE HELPERS ====================

    private void validateTeacherForSession(String sessionId) {
        try {
            VideoSessionDto session = videoSessionService.getSessionById(sessionId);
            String senderUserId = getCurrentUserId();

            if (!senderUserId.equals(session.getTeacherId())) {
                log.warn("❌ Unauthorized user {} trying to control session {}",
                        senderUserId, sessionId);
                throw new IllegalArgumentException(
                        "Only teacher can send host control messages");
            }
            log.debug("✅ Teacher {} authorized for session {}", senderUserId, sessionId);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Teacher validation failed for session {}: {}",
                    sessionId, e.getMessage());
            throw new IllegalArgumentException("Invalid session or unauthorized access");
        }
    }

    /**
     * Returns current userId from STOMP session.
     * TODO: Extract from STOMP session attributes when WebSocket auth is added.
     */
    private String getCurrentUserId() {
        return "unknown-user";
    }
}