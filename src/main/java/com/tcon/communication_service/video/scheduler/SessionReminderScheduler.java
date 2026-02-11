package com.tcon.communication_service.video.scheduler;

import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Session Reminder Scheduler
 * Sends reminders to users before their scheduled video sessions
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionReminderScheduler {

    private final VideoSessionRepository videoSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Check every minute for upcoming sessions
     * Sends reminders at 10 minutes and 5 minutes before session starts
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void sendSessionReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in15Minutes = now.plusMinutes(15);

        // Find sessions starting in the next 15 minutes
        List<VideoSession> upcomingSessions = videoSessionRepository
                .findByStatusAndScheduledStartTimeBetween(
                        SessionStatus.SCHEDULED,
                        now,
                        in15Minutes
                );

        for (VideoSession session : upcomingSessions) {
            long minutesUntilStart = java.time.Duration
                    .between(now, session.getScheduledStartTime())
                    .toMinutes();

            // Send reminders at 10 and 5 minutes
            if (minutesUntilStart == 10 || minutesUntilStart == 5) {
                sendReminders(session, (int) minutesUntilStart);
            }

            // Notify when session time arrives
            if (minutesUntilStart <= 0 && minutesUntilStart >= -1) {
                notifySessionStart(session);
            }
        }
    }

    /**
     * Send reminder notifications to all participants
     */
    private void sendReminders(VideoSession session, int minutesUntilStart) {
        Map<String, Object> reminderData = Map.of(
                "type", "SESSION_REMINDER",
                "sessionId", session.getId(),
                "classSessionId", session.getClassSessionId(),
                "minutesUntilStart", minutesUntilStart,
                "scheduledStartTime", session.getScheduledStartTime().toString(),
                "subject", session.getMetadata().getSubject() != null ?
                        session.getMetadata().getSubject() : "Class Session",
                "durationMinutes", session.getDurationMinutes() != null ?
                        session.getDurationMinutes() : 60
        );

        // Send to teacher
        sendReminderToUser(session.getTeacherId(), reminderData);
        log.info("📢 Reminder sent to teacher {} for session {}", session.getTeacherId(), session.getId());

        // Send to student
        sendReminderToUser(session.getStudentId(), reminderData);
        log.info("📢 Reminder sent to student {} for session {}", session.getStudentId(), session.getId());

        // Send to parent if present
        if (session.getParentId() != null && !session.getParentId().isEmpty()) {
            sendReminderToUser(session.getParentId(), reminderData);
            log.info("📢 Reminder sent to parent {} for session {}", session.getParentId(), session.getId());
        }

        log.info("✅ Session reminders sent: {} ({} minutes before)", session.getId(), minutesUntilStart);
    }

    /**
     * Send reminder to a specific user via WebSocket
     */
    private void sendReminderToUser(String userId, Map<String, Object> data) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/session-reminder",
                    data
            );
        } catch (Exception e) {
            log.error("❌ Failed to send reminder to user: {}", userId, e);
        }
    }

    /**
     * Notify users that session is starting now
     */
    private void notifySessionStart(VideoSession session) {
        Map<String, Object> startNotification = Map.of(
                "type", "SESSION_START",
                "sessionId", session.getId(),
                "classSessionId", session.getClassSessionId(),
                "message", "Your class is starting now!",
                "subject", session.getMetadata().getSubject() != null ?
                        session.getMetadata().getSubject() : "Class Session",
                "timestamp", System.currentTimeMillis()
        );

        // Notify teacher
        sendReminderToUser(session.getTeacherId(), startNotification);

        // Notify student
        sendReminderToUser(session.getStudentId(), startNotification);

        // Notify parent if present
        if (session.getParentId() != null && !session.getParentId().isEmpty()) {
            sendReminderToUser(session.getParentId(), startNotification);
        }

        log.info("🔔 Session start notifications sent for: {}", session.getId());
    }

    /**
     * Clean up sessions that got stuck in IN_PROGRESS status
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupStuckSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(3);
        List<VideoSession> stuckSessions = videoSessionRepository
                .findStuckInProgressSessions(threshold);

        if (!stuckSessions.isEmpty()) {
            log.warn("⚠️ Found {} stuck sessions", stuckSessions.size());
        }

        for (VideoSession session : stuckSessions) {
            log.warn("⚠️ Cleaning up stuck session: {}, marking as completed", session.getId());
            session.endSession();
            videoSessionRepository.save(session);
        }
    }

    /**
     * Mark sessions as NO_SHOW if nobody joined within grace period
     * Runs every hour
     */
    @Scheduled(fixedRate = 3600000) // Every 1 hour
    public void markNoShowSessions() {
        LocalDateTime gracePeriod = LocalDateTime.now().minusMinutes(30);

        List<VideoSession> noShowSessions = videoSessionRepository
                .findByStatusAndScheduledStartTimeBefore(
                        SessionStatus.SCHEDULED,
                        gracePeriod
                );

        for (VideoSession session : noShowSessions) {
            // Check if any participant joined
            boolean anyoneJoined = session.getParticipants() != null &&
                    !session.getParticipants().isEmpty();

            if (!anyoneJoined) {
                log.warn("⚠️ Marking session as NO_SHOW: {}", session.getId());
                session.setStatus(SessionStatus.NO_SHOW);
                videoSessionRepository.save(session);
            }
        }
    }
}
