package com.tcon.communication_service.video.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    @Builder.Default
    private Boolean isCameraEnabled = true;

    @Builder.Default
    private Boolean isMicEnabled = true;

    @Builder.Default
    private Boolean isScreenSharing = false;

    private Integer durationMinutes;

    // Calculate duration when participant leaves
    public void leave() {
        this.leftAt = LocalDateTime.now();
        if (this.joinedAt != null) {
            this.durationMinutes = (int) java.time.Duration
                    .between(this.joinedAt, this.leftAt)
                    .toMinutes();
        }
    }

    public boolean isActive() {
        return leftAt == null;
    }
}
