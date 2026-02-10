package com.tcon.communication_service.video.scheduler;

import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSessionScheduler {

    private final VideoSessionRepository videoSessionRepository;

    /**
     * Auto-end sessions that have exceeded their scheduled duration
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void autoEndExpiredSessions() {
        log.debug("🔍 Checking for expired video sessions...");

        LocalDateTime now = LocalDateTime.now();
        List<VideoSession> inProgressSessions = videoSessionRepository
                .findByStatusAndScheduledStartTimeBefore(SessionStatus.IN_PROGRESS, now);

        for (VideoSession session : inProgressSessions) {
            LocalDateTime expectedEndTime = session.getScheduledStartTime()
                    .plusMinutes(session.getDurationMinutes())
                    .plusMinutes(15); // 15 min grace period

            if (now.isAfter(expectedEndTime)) {
                log.warn("⏰ Auto-ending expired session: {}", session.getId());
                session.endSession();
                videoSessionRepository.save(session);
            }
        }
    }

    /**
     * Clean up stuck sessions
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupStuckSessions() {
        log.info("🧹 Cleaning up stuck sessions...");

        // Sessions stuck in IN_PROGRESS for more than 24 hours
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<VideoSession> stuckSessions = videoSessionRepository
                .findStuckInProgressSessions(threshold);

        for (VideoSession session : stuckSessions) {
            log.warn("🔧 Fixing stuck session: {}", session.getId());
            session.endSession();
            videoSessionRepository.save(session);
        }
    }
}
