package com.tcon.communication_service.video.event;

import com.tcon.communication_service.video.dto.RoomCreateRequest;
import com.tcon.communication_service.video.service.VideoSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

            if ("BOOKING_CONFIRMED".equals(eventType)
                    || "BOOKING_APPROVED".equals(eventType)) {
                createVideoSession(event);
            }
        } catch (Exception e) {
            log.error("❌ Error processing booking event: {}", e.getMessage(), e);
        }
    }

    private void createVideoSession(Map<String, Object> event) {
        try {
            String bookingId = sanitize((String) event.get("bookingId"));
            String teacherId = sanitize((String) event.get("teacherId"));
            String studentId = sanitize((String) event.get("studentId"));
            String parentId = sanitize((String) event.get("parentId"));
            String subject = sanitize((String) event.get("subject"));

            String classSessionId = sanitize((String) event.get("classSessionId"));
            if (classSessionId == null) {
                classSessionId = sanitize((String) event.get("sessionId"));
                if (classSessionId != null) {
                    log.warn("⚠️ classSessionId missing — using sessionId: {}", classSessionId);
                }
            }
            if (classSessionId == null) {
                classSessionId = bookingId;
                log.warn("⚠️ sessionId also missing — using bookingId as classSessionId: {}", bookingId);
            }

            log.info("🎥 Kafka: Creating video session");
            log.info("   📋 classSessionId : {}", classSessionId);
            log.info("   📋 bookingId      : {}", bookingId);
            log.info("   👨‍🏫 teacherId      : {}", teacherId);
            log.info("   👨‍🎓 studentId      : {}", studentId);
            log.info("   👪 parentId       : {}", parentId != null ? parentId : "none");

            if (classSessionId == null) {
                log.error("❌ No usable session ID found in event. Skipping.");
                return;
            }
            if (teacherId == null) {
                log.error("❌ teacherId missing. Skipping.");
                return;
            }
            if (studentId == null) {
                log.error("❌ studentId missing. Skipping.");
                return;
            }

            Integer durationMinutes = null;
            Object durationObj = event.get("durationMinutes");
            if (durationObj instanceof Number) {
                durationMinutes = ((Number) durationObj).intValue();
            }

            Instant scheduledStartTime = parseInstant(
                    event.get("scheduledStartTime"), "scheduledStartTime");

            if (scheduledStartTime == null) {
                log.warn("⚠️ scheduledStartTime missing or unparseable. " +
                        "Creating session without time — canJoin() will use status only.");
            }

            Instant scheduledEndTime = parseInstant(
                    event.get("scheduledEndTime"), "scheduledEndTime");

            if (scheduledEndTime == null
                    && scheduledStartTime != null
                    && durationMinutes != null) {
                scheduledEndTime = scheduledStartTime.plusSeconds((long) durationMinutes * 60);
                log.info("📅 Calculated scheduledEndTime: {}", scheduledEndTime);
            }

            RoomCreateRequest request = RoomCreateRequest.builder()
                    .classSessionId(classSessionId)
                    .bookingId(bookingId)
                    .teacherId(teacherId)
                    .studentId(studentId)
                    .parentId(parentId)
                    .scheduledStartTime(scheduledStartTime)
                    .scheduledEndTime(scheduledEndTime)
                    .durationMinutes(durationMinutes)
                    .subject(subject)
                    .recordingEnabled(true)
                    .whiteboardEnabled(true)
                    .chatEnabled(true)
                    .build();

            videoSessionService.createRoom(request);

            log.info("✅ Kafka: Video session created/found for bookingId={}", bookingId);
            log.info("   📅 Start : {}", scheduledStartTime);
            log.info("   📅 End   : {}", scheduledEndTime);

        } catch (Exception e) {
            log.error("❌ Kafka: Failed to create video session: {}", e.getMessage(), e);
        }
    }

    private Instant parseInstant(Object value, String fieldName) {
        if (value == null) {
            log.warn("⚠️ {} is null", fieldName);
            return null;
        }

        String str = value.toString().trim();
        log.debug("📅 Parsing {}: '{}'", fieldName, str);

        if (str.startsWith("[")) {
            try {
                str = str.replaceAll("[\\[\\]\\s]", "");
                String[] parts = str.split(",");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                int hour = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                int minute = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
                int second = parts.length > 5 ? Integer.parseInt(parts[5]) : 0;

                Instant result = LocalDateTime.of(year, month, day, hour, minute, second)
                        .toInstant(ZoneOffset.UTC);

                log.info("✅ Parsed {} from array: {}", fieldName, result);
                return result;
            } catch (Exception e) {
                log.error("❌ Failed to parse {} from array format '{}': {}",
                        fieldName, str, e.getMessage());
                return null;
            }
        }

        if (str.endsWith("Z")) {
            try {
                Instant result = Instant.parse(str);
                log.info("✅ Parsed {} as Instant: {}", fieldName, result);
                return result;
            } catch (DateTimeParseException e) {
                log.warn("⚠️ Could not parse {} as Instant: {}", fieldName, str);
            }
        }

        if (str.contains("+")) {
            try {
                Instant result = ZonedDateTime.parse(str).toInstant();
                log.info("✅ Parsed {} from ZonedDateTime: {}", fieldName, result);
                return result;
            } catch (DateTimeParseException e) {
                log.warn("⚠️ Could not parse {} as ZonedDateTime: {}", fieldName, str);
            }
        }

        try {
            Instant result = LocalDateTime.parse(str).toInstant(ZoneOffset.UTC);
            log.info("✅ Parsed {} as UTC LocalDateTime fallback: {}", fieldName, result);
            return result;
        } catch (DateTimeParseException e) {
            log.warn("⚠️ Could not parse {} as plain LocalDateTime: {}", fieldName, str);
        }

        try {
            Instant result = LocalDateTime.parse(
                    str, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            ).toInstant(ZoneOffset.UTC);
            log.info("✅ Parsed {} with millis fallback: {}", fieldName, result);
            return result;
        } catch (DateTimeParseException e) {
            log.warn("⚠️ Could not parse {} with millis: {}", fieldName, str);
        }

        try {
            Instant result = LocalDateTime.parse(str + "T00:00:00")
                    .toInstant(ZoneOffset.UTC);
            log.info("✅ Parsed {} from date-only fallback: {}", fieldName, result);
            return result;
        } catch (DateTimeParseException e) {
            log.warn("⚠️ Could not parse {} in any format: '{}'", fieldName, str);
        }

        log.error("❌ All parsing attempts failed for {} = '{}'", fieldName, str);
        return null;
    }

    private String sanitize(String value) {
        return (value != null && !value.isBlank()) ? value : null;
    }
}