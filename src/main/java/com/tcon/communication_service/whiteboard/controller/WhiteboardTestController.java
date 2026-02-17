package com.tcon.communication_service.whiteboard.controller;

import com.tcon.communication_service.config.AgoraWhiteboardConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/whiteboard/test")
@RequiredArgsConstructor
public class WhiteboardTestController {

    private final AgoraWhiteboardConfig config;

    @GetMapping("/config")
    public Map<String, String> testConfig() {
        Map<String, String> result = new HashMap<>();
        result.put("appId", maskKey(config.getAppId()));
        result.put("accessKey", maskKey(config.getAccessKey()));
        result.put("secretKey", maskKey(config.getSecretKey()));
        result.put("region", config.getRegion());
        result.put("apiBaseUrl", config.getApiBaseUrl());
        return result;
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}