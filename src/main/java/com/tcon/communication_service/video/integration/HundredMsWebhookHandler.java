package com.tcon.communication_service.video.integration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 100ms Webhook Handler
 * Handles webhooks from 100ms for recording status updates
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/hundredms")
@RequiredArgsConstructor
public class HundredMsWebhookHandler {

    private final VideoSessionRepository videoSessionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handle recording webhook
     */
    @PostMapping("/recording")
    public ResponseEntity<Void> handleRecordingWebhook(@RequestBody String payload) {
        log.info("Received 100ms recording webhook");

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String eventType = jsonNode.get("type").asText();
            String roomId = jsonNode.get("room_id").asText();

            log.info("Webhook event type: {} for room: {}", eventType, roomId);

            VideoSession session = videoSessionRepository.findByHundredMsRoomId(roomId)
                    .orElse(null);

            if (session == null) {
                log.warn("Session not found for room: {}", roomId);
                return ResponseEntity.ok().build();
            }

            switch (eventType) {
                case "recording.started" -> handleRecordingStarted(session, jsonNode);
                case "recording.stopped" -> handleRecordingStopped(session, jsonNode);
                case "recording.success" -> handleRecordingSuccess(session, jsonNode);
                case "recording.failed" -> handleRecordingFailed(session, jsonNode);
                default -> log.warn("Unknown webhook event type: {}", eventType);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void handleRecordingStarted(VideoSession session, JsonNode payload) {
        String recordingId = payload.get("recording_id").asText();
        session.setRecordingId(recordingId);
        session.setRecordingStatus("RECORDING");
        videoSessionRepository.save(session);
        log.info("Recording started for session: {}", session.getId());
    }

    private void handleRecordingStopped(VideoSession session, JsonNode payload) {
        session.setRecordingStatus("STOPPED");
        videoSessionRepository.save(session);
        log.info("Recording stopped for session: {}", session.getId());
    }

    private void handleRecordingSuccess(VideoSession session, JsonNode payload) {
        String recordingUrl = payload.get("recording_url").asText();
        session.setRecordingUrl(recordingUrl);
        session.setRecordingStatus("COMPLETED");
        videoSessionRepository.save(session);
        log.info("Recording completed for session: {}", session.getId());
    }

    private void handleRecordingFailed(VideoSession session, JsonNode payload) {
        session.setRecordingStatus("FAILED");
        videoSessionRepository.save(session);
        log.error("Recording failed for session: {}", session.getId());
    }
}
