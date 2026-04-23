package com.tcon.communication_service.config;

import com.tcon.communication_service.websocket.JwtHandshakeInterceptor;
import com.tcon.communication_service.websocket.UserIdHandshakeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        log.info("🔧 [WebSocket] Configuring STOMP message broker");

        // Client sends to: /app/...
        config.setApplicationDestinationPrefixes("/app");

        // Server broadcasts to: /topic, /queue, /user
        config.enableSimpleBroker("/topic", "/queue", "/user");

        // Per-user destinations: /user/queue/...
        config.setUserDestinationPrefix("/user");

        log.info("✅ [WebSocket] Message broker configured");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("🔧 [WebSocket] Registering STOMP endpoint: /ws-messaging");

        registry.addEndpoint("/ws-messaging")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(new UserIdHandshakeHandler())
                .withSockJS()
                .setWebSocketEnabled(true)
                .setSessionCookieNeeded(false)
                .setStreamBytesLimit(512 * 1024)
                .setDisconnectDelay(30 * 1000)
                .setHeartbeatTime(25 * 1000)
                .setSuppressCors(true);

        log.info("✅ [WebSocket] STOMP endpoint /ws-messaging registered with SockJS + JWT auth");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        log.info("🔧 [WebSocket] Configuring WebSocket transport limits");

        registration
                .setMessageSizeLimit(512 * 1024)      // 512 KB per message
                .setSendBufferSizeLimit(1024 * 1024)  // 1 MB send buffer
                .setSendTimeLimit(20 * 1000)          // 20 seconds to send
                .setTimeToFirstMessage(30 * 1000);    // 30 seconds handshake -> first msg

        log.info("✅ [WebSocket] WebSocket transport configured");
    }
}