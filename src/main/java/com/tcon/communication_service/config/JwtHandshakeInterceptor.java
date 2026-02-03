package com.tcon.communication_service.messaging.interceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        String path = request.getURI().getPath();
        log.info("🔍 [JWT] ===== WebSocket Handshake Starting =====");
        log.info("🔍 [JWT] Request URI: {}", request.getURI());
        log.info("🔍 [JWT] Request Path: {}", path);

        // ===== SKIP JWT validation for SockJS /info endpoint =====
        // This is just metadata - like the lobby of a building
        // Real authentication happens on WebSocket upgrade
        if (path.endsWith("/info")) {
            log.info("✅ [JWT] Skipping JWT validation for /info endpoint (public metadata)");
            return true;
        }

        String token = null;

        // Try to get token from query parameter
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            token = servletRequest.getServletRequest().getParameter("access_token");

            if (token != null) {
                log.info("✅ [JWT] Token found in query parameter (length: {})", token.length());
            } else {
                log.warn("⚠️ [JWT] No token in query parameter");
            }
        }

        if (token == null || token.isEmpty()) {
            log.error("❌ [JWT] No token provided - REJECTING handshake");
            log.error("❌ [JWT] Full URI: {}", request.getURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            log.debug("🔑 [JWT] Validating token...");

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            attributes.put("userId", userId);
            attributes.put("email", email);
            attributes.put("role", role);
            attributes.put("token", token);

            log.info("🎉 ✅ [JWT] WebSocket authenticated: userId={}, email={}", userId, email);
            return true;

        } catch (Exception e) {
            log.error("❌ [JWT] Invalid token: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            log.error("❌ [JWT] Handshake failed: {}", exception.getMessage());
        } else {
            log.info("✅ [JWT] Handshake completed successfully");
        }
    }
}
