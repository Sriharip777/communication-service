package com.tcon.communication_service.video.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSettings {

    @Builder.Default
    private Boolean whiteboardEnabled = true;

    @Builder.Default
    private Boolean screenShareEnabled = true;

    @Builder.Default
    private Boolean chatEnabled = true;

    @Builder.Default
    private Boolean handRaiseEnabled = true;

    @Builder.Default
    private Boolean recordingEnabled = true;

    private String recordingQuality; // 720p, 1080p

    @Builder.Default
    private Integer maxParticipants = 10;
}
