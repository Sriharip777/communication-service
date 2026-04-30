package com.tcon.communication_service.video.scheduler;

import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Session Reminder Scheduler
 * Sends reminders to users before their scheduled video sessions
 * and performs lightweight session status maintenance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionReminderScheduler {

    private final VideoSessionRepository videoSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Check every minute for upcoming sessions.
     * Sends reminders at 10 minutes and 5 minutes before session starts,
     * and a start notification when the session time arrives.
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void sendSessionReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in15Minutes = now.plusMinutes(15);

        List<VideoSession> upcomingSessions = videoSessionRepository
                .findByStatusAndScheduledStartTimeBetween(
                        SessionStatus.SCHEDULED,
                        now,
                        in15Minutes
                );

        for (VideoSession session : upcomingSessions) {
            if (session.getScheduledStartTime() == null) {
                continue;
            }

            long minutesUntilStart = Duration
                    .between(now, session.getScheduledStartTime())
                    .toMinutes();

            if (minutesUntilStart == 10 || minutesUntilStart == 5) {
                sendReminders(session, (int) minutesUntilStart);
            }

            if (minutesUntilStart <= 0 && minutesUntilStart >= -1) {
                notifySessionStart(session);
            }
        }
    }

    /**
     * Clean up sessions that got stuck in IN_PROGRESS status.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
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
     * Mark expired scheduled sessions as NO_SHOW if nobody joined,
     * or COMPLETED if participants had joined but status never moved.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void markNoShowSessions() {
        LocalDateTime now = LocalDateTime.now();

        List<VideoSession> scheduledSessions =
                videoSessionRepository.findByStatus(SessionStatus.SCHEDULED);

        for (VideoSession session : scheduledSessions) {
            LocalDateTime end = resolveSessionEndTime(session);

            if (end == null) {
                continue;
            }

            if (now.isBefore(end)) {
                continue;
            }

            boolean anyoneJoined = session.getParticipants() != null
                    && !session.getParticipants().isEmpty();

            if (!anyoneJoined) {
                log.warn("⏰ Marking session as NO_SHOW after scheduled end: {}", session.getId());
                session.setStatus(SessionStatus.NO_SHOW);
                session.setEndTime(now);
                videoSessionRepository.save(session);
            } else {
                log.info("⏰ Expired scheduled session had participants, marking COMPLETED: {}", session.getId());

                if (session.getParticipants() != null) {
                    session.getParticipants().stream()
                            .filter(p -> p.getLeftAt() == null)
                            .forEach(p -> {
                                try {
                                    p.leave();
                                } catch (Exception e) {
                                    log.warn("⚠️ Failed to auto-close participant {} in session {}",
                                            p.getUserId(), session.getId());
                                }
                            });
                }

                session.endSession();
                videoSessionRepository.save(session);
            }
        }
    }

    /**
     * Send reminder notifications to all participants.
     */
    private void sendReminders(VideoSession session, int minutesUntilStart) {
        Map<String, Object> reminderData = Map.of(
                "type", "SESSION_REMINDER",
                "sessionId", session.getId(),
                "classSessionId", session.getClassSessionId(),
                "minutesUntilStart", minutesUntilStart,
                "scheduledStartTime", session.getScheduledStartTime().toString(),
                "subject", session.getMetadata() != null && session.getMetadata().getSubject() != null
                        ? session.getMetadata().getSubject()
                        : "Class Session",
                "durationMinutes", session.getDurationMinutes() != null
                        ? session.getDurationMinutes()
                        : 60
        );

        sendReminderToUser(session.getTeacherId(), reminderData);
        log.info("📢 Reminder sent to teacher {} for session {}", session.getTeacherId(), session.getId());

        sendReminderToUser(session.getStudentId(), reminderData);
        log.info("📢 Reminder sent to student {} for session {}", session.getStudentId(), session.getId());

        if (session.getParentId() != null && !session.getParentId().isEmpty()) {
            sendReminderToUser(session.getParentId(), reminderData);
            log.info("📢 Reminder sent to parent {} for session {}", session.getParentId(), session.getId());
        }

        log.info("✅ Session reminders sent: {} ({} minutes before)", session.getId(), minutesUntilStart);
    }

    /**
     * Send reminder to a specific user via WebSocket.
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
     * Notify users that session is starting now.
     */
    private void notifySessionStart(VideoSession session) {
        Map<String, Object> startNotification = Map.of(
                "type", "SESSION_START",
                "sessionId", session.getId(),
                "classSessionId", session.getClassSessionId(),
                "message", "Your class is starting now!",
                "subject", session.getMetadata() != null && session.getMetadata().getSubject() != null
                        ? session.getMetadata().getSubject()
                        : "Class Session",
                "timestamp", System.currentTimeMillis()
        );

        sendReminderToUser(session.getTeacherId(), startNotification);
        sendReminderToUser(session.getStudentId(), startNotification);

        if (session.getParentId() != null && !session.getParentId().isEmpty()) {
            sendReminderToUser(session.getParentId(), startNotification);
        }

        log.info("🔔 Session start notifications sent for: {}", session.getId());
    }

    /**
     * Resolve session end time from scheduledEndTime or scheduledStartTime + durationMinutes.
     */
    private LocalDateTime resolveSessionEndTime(VideoSession session) {
        if (session.getScheduledEndTime() != null) {
            return session.getScheduledEndTime();
        }

        if (session.getScheduledStartTime() != null && session.getDurationMinutes() != null) {
            return session.getScheduledStartTime().plusMinutes(session.getDurationMinutes());
        }

        return null;
    }
}