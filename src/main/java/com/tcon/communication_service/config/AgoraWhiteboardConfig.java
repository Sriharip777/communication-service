package com.tcon.communication_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agora Interactive Whiteboard Configuration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agora.whiteboard")
public class AgoraWhiteboardConfig {
    private String appId;
    private String accessKey;
    private String secretKey;
    private String apiBaseUrl;
    private String region;
    private Integer tokenExpiryHours = 24;
}