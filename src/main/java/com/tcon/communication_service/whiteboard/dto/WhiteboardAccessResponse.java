package com.tcon.communication_service.whiteboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhiteboardAccessResponse {
    private String agoraWhiteboardUuid;
    private String roomToken;
    private String role;
    private String appId;
    private String region;
    private Long expiresAt;
}