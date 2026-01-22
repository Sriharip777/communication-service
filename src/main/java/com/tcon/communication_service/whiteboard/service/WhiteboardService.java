package com.tcon.communication_service.whiteboard.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory whiteboard state store (Redis can replace this later).
 */
@Slf4j
@Service
public class WhiteboardService {

    private final Map<String, String> boardStateBySession = new ConcurrentHashMap<>();

    public void updateState(String sessionId, String stateJson) {
        boardStateBySession.put(sessionId, stateJson);
        log.debug("Updated whiteboard state for session {}", sessionId);
    }

    public String getState(String sessionId) {
        return boardStateBySession.getOrDefault(sessionId, "{}");
    }
}
