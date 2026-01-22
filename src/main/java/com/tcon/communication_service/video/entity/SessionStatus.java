package com.tcon.communication_service.video.entity;


/**
 * Session Status Enum
 * Defines the current status of a video session
 */
public enum SessionStatus {
    SCHEDULED,      // Session created but not started
    IN_PROGRESS,    // Session is currently active
    COMPLETED,      // Session ended normally
    CANCELLED,      // Session was cancelled
    NO_SHOW         // Neither party joined within grace period
}
