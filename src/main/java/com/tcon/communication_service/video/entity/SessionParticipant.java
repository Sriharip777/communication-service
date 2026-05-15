package com.tcon.communication_service.video.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Session Participant
 * Embedded document representing a participant in a video session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionParticipant {

    private String userId;
    private ParticipantRole role;
    private String hundredMsPeerId;

    private Instant joinedAt;
    private Instant leftAt;

    @Builder.Default
    private Boolean isCameraEnabled = true;

    @Builder.Default
    private Boolean isMicEnabled = true;

    @Builder.Default
    private Boolean isScreenSharing = false;

    private Integer durationMinutes;

    /**
     * Mark participant as left and calculate duration in minutes.
     */
    public void leave() {
        this.leftAt = Instant.now();
        if (this.joinedAt != null) {
            this.durationMinutes = (int) Duration
                    .between(this.joinedAt, this.leftAt)
                    .toMinutes();
        }
    }

    public boolean isActive() {
        return this.leftAt == null;
    }
}