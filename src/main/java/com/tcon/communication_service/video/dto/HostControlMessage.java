package com.tcon.communication_service.video.dto;

import lombok.Data;

@Data
public class HostControlMessage {
    private String sessionId;     // video session id
    private String targetUserId;  // parent user id
    private String action;        // "ALLOW_SPEAK" or "REVOKE_SPEAK"
}