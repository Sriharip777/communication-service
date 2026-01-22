package com.tcon.communication_service.video.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session Metadata
 * Additional information about the video session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMetadata {

    @Builder.Default
    private Boolean whiteboardEnabled = true;

    @Builder.Default
    private Boolean chatEnabled = true;

    @Builder.Default
    private Boolean screenShareEnabled = true;

    @Builder.Default
    private Boolean handRaiseEnabled = true;

    private String roomQuality; // 720p, 1080p

    private Integer maxParticipants;

    private String subject; // Subject being taught

    private String notes; // Session notes
}

