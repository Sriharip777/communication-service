package com.tcon.communication_service.messaging.entity;

/**
 * Message Status Enum
 * Defines the delivery status of a message
 */
public enum MessageStatus {
    SENT,       // Message sent but not delivered
    DELIVERED,  // Message delivered to recipient
    READ        // Message read by recipient
}
