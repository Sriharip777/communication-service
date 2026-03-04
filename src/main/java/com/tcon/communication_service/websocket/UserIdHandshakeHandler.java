package com.tcon.communication_service.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Slf4j
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String userId = (String) attributes.get("userId");
        if (userId == null) {
            log.warn("⚠️ No userId in attributes, using default principal");
            return super.determineUser(request, wsHandler, attributes);
        }

        log.info("👤 [WebSocket] Using userId {} as Principal name", userId);
        return () -> userId;   // Principal#getName() returns userId
    }
}