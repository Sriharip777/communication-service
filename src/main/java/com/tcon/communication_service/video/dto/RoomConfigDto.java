package com.tcon.communication_service.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Room Config DTO
 * Configuration for the video room
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomConfigDto {

    @Builder.Default
    private Boolean recordingEnabled = true;

    @Builder.Default
    private Boolean whiteboardEnabled = true;

    @Builder.Default
    private Boolean chatEnabled = true;

    @Builder.Default
    private Boolean screenShareEnabled = true;

    @Builder.Default
    private String quality = "720p";

    private Integer maxParticipants;
}
