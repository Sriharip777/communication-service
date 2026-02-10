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

            RoomCreateRequest request = RoomCreateRequest.builder()
                    .classSessionId(classSessionId)
                    .teacherId((String) event.get("teacherId"))
                    .studentId((String) event.get("studentId"))
                    .parentId((String) event.get("parentId"))
                    .scheduledStartTime(LocalDateTime.parse((String) event.get("scheduledStartTime")))
                    .durationMinutes((Integer) event.get("durationMinutes"))
                    .subject((String) event.get("subject"))
                    .recordingEnabled(true)
                    .whiteboardEnabled(true)
                    .chatEnabled(true)
                    .build();

            videoSessionService.createRoom(request);
            log.info("✅ Video session created for one-on-one class: {}", classSessionId);

        } catch (Exception e) {
            log.error("❌ Failed to create video session: {}", e.getMessage(), e);
        }
    }
}
