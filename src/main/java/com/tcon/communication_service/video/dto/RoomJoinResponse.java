package com.tcon.communication_service.video.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Room Join Response DTO
 * Response containing room join details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomJoinResponse {

    private String sessionId;
    private String roomId;
    private String roomName;
    private String authToken;
    private String peerId;
    private String role;
    private RoomConfigDto roomConfig;
    private String webrtcUrl;
}
