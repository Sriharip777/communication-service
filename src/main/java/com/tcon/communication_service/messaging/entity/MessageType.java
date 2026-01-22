package com.tcon.communication_service.messaging.entity;


/**
 * Message Type Enum
 * Defines the type of message content
 */
public enum MessageType {
    TEXT,       // Plain text message
    IMAGE,      // Image attachment
    FILE,       // File attachment
    AUDIO,      // Audio message
    VIDEO,      // Video message
    LOCATION,   // Location share
    SYSTEM      // System generated message
}
