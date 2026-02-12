package com.tcon.communication_service.config;
import com.tcon.communication_service.websocket.JwtHandshakeInterceptor;
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
        log.info("🔧 Configuring message broker");

        config.enableSimpleBroker("/topic", "/queue", "/user");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");

        log.info("✅ Message broker configured");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("🔧 Registering STOMP endpoint: /ws-messaging");

        registry.addEndpoint("/ws-messaging")
                .setAllowedOriginPatterns("*")
                //.addInterceptors(jwtHandshakeInterceptor)
                .withSockJS()
                .setWebSocketEnabled(true)
                .setSessionCookieNeeded(false)
                .setStreamBytesLimit(512 * 1024)
                .setDisconnectDelay(30 * 1000)
                .setHeartbeatTime(25 * 1000)
                .setSuppressCors(true);

        log.warn(" STOMP endpoint registered WITHOUT authentication (TESTING) ");

    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        log.info("🔧 Configuring WebSocket transport");

        registration
                .setMessageSizeLimit(512 * 1024)
                .setSendBufferSizeLimit(1024 * 1024)
                .setSendTimeLimit(20 * 1000)
                .setTimeToFirstMessage(30 * 1000);

        log.info("✅ WebSocket transport configured");
    }
}