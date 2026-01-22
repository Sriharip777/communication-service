package com.tcon.communication_service.messaging.entity;


/**
 * Report Status Enum
 * Status of a message report
 */
public enum ReportStatus {
    PENDING,    // Awaiting review
    REVIEWED,   // Reviewed by moderator
    APPROVED,   // Report approved, action taken
    REJECTED    // Report rejected, no action
}
