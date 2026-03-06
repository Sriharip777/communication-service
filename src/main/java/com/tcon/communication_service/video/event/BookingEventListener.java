package com.tcon.communication_service.video.event;

import com.tcon.communication_service.video.dto.RoomCreateRequest;
import com.tcon.communication_service.video.service.VideoSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final VideoSessionService videoSessionService;

    @KafkaListener(
            topics = "${spring.kafka.topics.booking-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleBookingEvent(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            log.info("📨 Received booking event: {}", eventType);

            if ("BOOKING_CONFIRMED".equals(eventType)) {
                createVideoSession(event);
            }
        } catch (Exception e) {
            log.error("❌ Error processing booking event: {}", e.getMessage(), e);
        }
    }

    private void createVideoSession(Map<String, Object> event) {
        try {
            String classSessionId = (String) event.get("classSessionId");

            log.info("🎥 Creating video session for class: {}", classSessionId);

            // Check if session already exists
            if (videoSessionService.existsByClassSessionId(classSessionId)) {
                log.info("ℹ️ Video session already exists for class: {}", classSessionId);
                return;
            }

            // ✅ Sanitize parentId: store null instead of empty string
            String parentId = (String) event.get("parentId");
            String sanitizedParentId = (parentId != null && !parentId.isBlank())
                    ? parentId : null;

            log.info("👪 parentId from event: '{}' → sanitized: '{}'", parentId, sanitizedParentId);

            // ✅ Sanitize studentId as well for safety
            String studentId = (String) event.get("studentId");
            String sanitizedStudentId = (studentId != null && !studentId.isBlank())
                    ? studentId : null;

            // ✅ Sanitize teacherId
            String teacherId = (String) event.get("teacherId");
            String sanitizedTeacherId = (teacherId != null && !teacherId.isBlank())
                    ? teacherId : null;

            // ✅ Sanitize subject
            String subject = (String) event.get("subject");
            String sanitizedSubject = (subject != null && !subject.isBlank())
                    ? subject : null;

            RoomCreateRequest request = RoomCreateRequest.builder()
                    .classSessionId(classSessionId)
                    .teacherId(sanitizedTeacherId)
                    .studentId(sanitizedStudentId)
                    .parentId(sanitizedParentId)           // ✅ null if empty
                    .scheduledStartTime(LocalDateTime.parse((String) event.get("scheduledStartTime")))
                    .durationMinutes((Integer) event.get("durationMinutes"))
                    .subject(sanitizedSubject)
                    .recordingEnabled(true)
                    .whiteboardEnabled(true)
                    .chatEnabled(true)
                    .build();

            videoSessionService.createRoom(request);
            log.info("✅ Video session created for one-on-one class: {}", classSessionId);
            log.info("   👨‍🏫 Teacher: {}", sanitizedTeacherId);
            log.info("   👨‍🎓 Student: {}", sanitizedStudentId);
            log.info("   👪 Parent: {}", sanitizedParentId != null ? sanitizedParentId : "none");

        } catch (Exception e) {
            log.error("❌ Failed to create video session: {}", e.getMessage(), e);
        }
    }
}