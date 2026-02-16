package com.tcon.communication_service.video.event;

import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.SessionMetadata;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import com.tcon.communication_service.video.integration.AgoraClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventListener {

    private final VideoSessionRepository videoSessionRepository;
    private final AgoraClient agoraClient;

    @KafkaListener(topics = "session-events", groupId = "communication-service")
    @Transactional
    public void handleSessionEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        log.info("📨 Received session event: {}", eventType);

        switch (eventType) {
            case "SESSION_CREATED":
                handleSessionCreated(event);
                break;
            case "SESSION_CANCELLED":
                handleSessionCancelled(event);
                break;
            default:
                log.debug("Ignoring event type: {}", eventType);
        }
    }

    private void handleSessionCreated(Map<String, Object> event) {
        try {
            String sessionId = (String) event.get("sessionId");
            String courseId = (String) event.get("courseId");
            String sessionType = (String) event.get("sessionType");
            String teacherId = (String) event.get("teacherId");
            String scheduledStartTimeStr = (String) event.get("scheduledStartTime");
            String scheduledEndTimeStr = (String) event.get("scheduledEndTime");
            Integer durationMinutes = (Integer) event.get("durationMinutes");
            Integer maxParticipants = (Integer) event.get("maxParticipants");
            String title = (String) event.get("title");

            log.info("🎥 Creating VideoSession for ClassSession: {}", sessionId);
            log.info("   📋 Type: {}, Course: {}", sessionType, courseId);

            if (videoSessionRepository.existsByClassSessionId(sessionId)) {
                log.warn("⚠️ VideoSession already exists for session: {}", sessionId);
                return;
            }

            LocalDateTime scheduledStartTime = LocalDateTime.parse(
                    scheduledStartTimeStr,
                    DateTimeFormatter.ISO_DATE_TIME
            );

            String roomName = agoraClient.createRoom(sessionId, durationMinutes);
            log.info("✅ Agora room created: {}", roomName);

            // ✅ Create metadata with course info
            SessionMetadata metadata = new SessionMetadata();
            metadata.setCourseId(courseId);
            metadata.setSessionType(sessionType);
            metadata.setTitle(title);
            metadata.setWhiteboardEnabled(true);
            metadata.setChatEnabled(true);
            metadata.setMaxParticipants(maxParticipants);

            VideoSession videoSession = VideoSession.builder()
                    .classSessionId(sessionId)
                    .teacherId(teacherId)
                    .studentId(null) // Group session - no single student
                    .parentId(null)
                    .hundredMsRoomId(roomName)
                    .hundredMsRoomName(roomName)
                    .status(SessionStatus.SCHEDULED)
                    .scheduledStartTime(scheduledStartTime)
                    .durationMinutes(durationMinutes)
                    .participants(new ArrayList<>())
                    .recordingEnabled(true)
                    .metadata(metadata)
                    .build();

            VideoSession saved = videoSessionRepository.save(videoSession);

            log.info("✅ VideoSession created successfully:");
            log.info("   🆔 VideoSession ID: {}", saved.getId());
            log.info("   🎓 ClassSession ID: {}", saved.getClassSessionId());
            log.info("   📹 Room: {}", saved.getHundredMsRoomName());
            log.info("   👥 Max Participants: {}", maxParticipants);
            log.info("   📅 Scheduled: {}", scheduledStartTime);

        } catch (Exception e) {
            log.error("❌ Failed to create VideoSession for session event", e);
            log.error("Event data: {}", event);
        }
    }

    private void handleSessionCancelled(Map<String, Object> event) {
        try {
            String sessionId = (String) event.get("sessionId");
            log.info("❌ Cancelling VideoSession for ClassSession: {}", sessionId);

            videoSessionRepository.findByClassSessionId(sessionId)
                    .ifPresent(videoSession -> {
                        videoSession.setStatus(SessionStatus.CANCELLED);
                        videoSessionRepository.save(videoSession);
                        log.info("✅ VideoSession cancelled: {}", videoSession.getId());
                    });

        } catch (Exception e) {
            log.error("❌ Failed to cancel VideoSession", e);
        }
    }
}