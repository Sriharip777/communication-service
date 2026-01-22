package com.tcon.communication_service.video.entity;


/**
 * Participant Role Enum
 * Defines the role of a participant in a video session
 */
public enum ParticipantRole {
    TEACHER,
    STUDENT,
    PARENT_OBSERVER  // Parent can join but camera/mic disabled by default
}

