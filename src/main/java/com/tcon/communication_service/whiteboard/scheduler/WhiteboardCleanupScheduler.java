package com.tcon.communication_service.whiteboard.scheduler;

import com.tcon.communication_service.whiteboard.service.WhiteboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhiteboardCleanupScheduler {

    private final WhiteboardService whiteboardService;

    @Value("${whiteboard.cleanup.abandoned-minutes:30}")
    private int abandonedMinutes;

    /**
     * ✅ Cleanup abandoned sessions every 5 minutes
     * Default: 30 minutes threshold (configurable via application.yml)
     */
    @Scheduled(fixedDelayString = "${whiteboard.cleanup.interval-ms:300000}")
    public void cleanupAbandonedSessions() {
        log.debug("🧹 Running whiteboard cleanup task...");

        try {
            whiteboardService.cleanupAbandonedSessions(abandonedMinutes);
            log.debug("✅ Cleanup task completed successfully");
        } catch (Exception e) {
            log.error("❌ Cleanup task failed: {}", e.getMessage(), e);
        }
    }
}