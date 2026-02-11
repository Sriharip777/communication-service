package com.tcon.communication_service.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agora Token Response DTO
 * Contains Agora RTC token and associated UID
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgoraTokenResponse {

    /**
     * Agora RTC token for authentication
     */
    private String token;

    /**
     * Numeric UID used to generate the token
     * Frontend must use this same UID when joining the channel
     */
    private Integer uid;

    /**
     * Channel/room name
     */
    private String channelName;

    /**
     * Token expiration timestamp (milliseconds)
     */
    private Long expiresAt;
}
