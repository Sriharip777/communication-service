package com.tcon.communication_service.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session Control Request DTO
 * Request to control session (mute, disable camera, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionControlRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "User ID is required")
    private String userId;

    private Boolean muteAudio;
    private Boolean muteVideo;
    private Boolean enableScreenShare;
    private Boolean raiseHand;
}
