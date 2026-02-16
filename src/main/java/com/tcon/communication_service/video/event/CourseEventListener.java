package com.tcon.communication_service.video.event;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventListener {

    private final VideoSessionRepository videoSessionRepository;

    @KafkaListener(topics = "course-events", groupId = "communication-service")
    public void handleCourseEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        log.info("📨 Received course event: {}", eventType);

        switch (eventType) {
            case "STUDENT_ENROLLED":
                handleStudentEnrolled(event);
                break;
            case "STUDENT_UNENROLLED":
                handleStudentUnenrolled(event);
                break;
            default:
                log.debug("Ignoring event type: {}", eventType);
        }
    }

    private void handleStudentEnrolled(Map<String, Object> event) {
        try {
            String courseId = (String) event.get("courseId");
            String studentId = (String) event.get("studentId");
            String studentName = (String) event.get("studentName");

            log.info("👨‍🎓 Student enrolled: {} in course: {}", studentName, courseId);
            log.info("🔓 Granting access to all video sessions for this course");

            // Find video sessions by courseId in metadata
            List<VideoSession> sessions = videoSessionRepository.findByCourseId(courseId);

            log.info("✅ Student {} now has access to {} video sessions",
                    studentId, sessions.size());

        } catch (Exception e) {
            log.error("❌ Failed to process student enrollment", e);
        }
    }

    private void handleStudentUnenrolled(Map<String, Object> event) {
        String courseId = (String) event.get("courseId");
        String studentId = (String) event.get("studentId");

        log.info("❌ Student unenrolled: {} from course: {}", studentId, courseId);
        log.info("🔒 Access to video sessions revoked");
    }
}