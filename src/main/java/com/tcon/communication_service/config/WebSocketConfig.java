package com.tcon.communication_service.config;

import com.tcon.communication_service.messaging.interceptor.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("🔧 Registering STOMP endpoint: /ws-messaging");

        registry.addEndpoint("/ws-messaging")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS()
                .setWebSocketEnabled(true);

        log.info("✅ STOMP endpoint registered successfully with SockJS support");
    }
}
