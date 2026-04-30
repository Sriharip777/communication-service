package com.tcon.communication_service.video.scheduler;

import com.tcon.communication_service.video.entity.SessionParticipant;
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
     * Auto-end sessions that exceeded scheduled end time + 15 min grace.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void autoEndExpiredSessions() {
        log.debug("🔍 Checking for expired IN_PROGRESS video sessions...");

        LocalDateTime now = LocalDateTime.now();
        List<VideoSession> inProgressSessions =
                videoSessionRepository.findByStatus(SessionStatus.IN_PROGRESS);

        for (VideoSession session : inProgressSessions) {
            LocalDateTime expectedEndTime = resolveExpectedEndTime(session);

            if (expectedEndTime == null) {
                log.warn("⚠️ Skipping session {} because end time cannot be resolved", session.getId());
                continue;
            }

            expectedEndTime = expectedEndTime.plusMinutes(15);

            if (now.isAfter(expectedEndTime)) {
                log.warn("⏰ Auto-ending expired IN_PROGRESS session: {}", session.getId());

                if (session.getParticipants() != null) {
                    session.getParticipants().stream()
                            .filter(SessionParticipant::isActive)
                            .forEach(SessionParticipant::leave);
                }

                session.endSession();
                videoSessionRepository.save(session);
            }
        }
    }

    private LocalDateTime resolveExpectedEndTime(VideoSession session) {
        if (session.getScheduledEndTime() != null) {
            return session.getScheduledEndTime();
        }

        if (session.getScheduledStartTime() != null && session.getDurationMinutes() != null) {
            return session.getScheduledStartTime().plusMinutes(session.getDurationMinutes());
        }

        return null;
    }
}