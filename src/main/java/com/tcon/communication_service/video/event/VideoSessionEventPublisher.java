package com.tcon.communication_service.video.event;

import com.tcon.communication_service.video.entity.VideoSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Video Session Event Publisher
 * Publishes video session events to Kafka
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSessionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.video-session-events}")
    private String topicName;

    public void publishSessionStarted(VideoSession session) {
        Map<String, Object> event = createBaseEvent("SESSION_STARTED", session);
        publish(event);
    }

    public void publishSessionEnded(VideoSession session) {
        Map<String, Object> event = createBaseEvent("SESSION_ENDED", session);
        event.put("actualDurationMinutes", session.getActualDurationMinutes());
        publish(event);
    }

    public void publishRecordingAvailable(VideoSession session) {
        Map<String, Object> event = createBaseEvent("RECORDING_AVAILABLE", session);
        event.put("recordingUrl", session.getRecordingUrl());
        publish(event);
    }

    private Map<String, Object> createBaseEvent(String eventType, VideoSession session) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("sessionId", session.getId());
        event.put("classSessionId", session.getClassSessionId());
        event.put("teacherId", session.getTeacherId());
        event.put("studentId", session.getStudentId());
        event.put("timestamp", java.time.Instant.now());
        return event;
    }

    private void publish(Map<String, Object> event) {
        try {
            kafkaTemplate.send(topicName, event);
            log.info("Published video session event: {}", event.get("eventType"));
        } catch (Exception e) {
            log.error("Error publishing video session event: {}", e.getMessage(), e);
        }
    }
}
