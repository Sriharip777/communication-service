package com.tcon.communication_service.websocket;

import com.tcon.communication_service.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        String uri = request.getURI().toString();
        log.debug("🔐 [WebSocket] Handshake started for: {}", uri);

        // ✅ FIX: Skip authentication for SockJS /info endpoint
        if (uri.contains("/info")) {
            log.debug("✅ [WebSocket] /info endpoint - allowing without authentication");
            return true;  // Allow /info requests without token
        }

        try {
            String token = extractToken(request);

            if (token == null) {
                log.warn("❌ [WebSocket] No token found in request");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Special case: Gateway pre-authenticated request
            if ("gateway-authenticated".equals(token)) {
                String userId = extractHeader(request, "X-User-Id");
                String userEmail = extractHeader(request, "X-User-Email");
                String userRole = extractHeader(request, "X-User-Role");

                if (userId != null) {
                    attributes.put("userId", userId);
                    attributes.put("userEmail", userEmail != null ? userEmail : "unknown");
                    attributes.put("userRole", userRole != null ? userRole : "USER");
                    log.info("🎉 ✅ [WebSocket] Gateway pre-authenticated: userId={}, email={}", userId, userEmail);
                    return true;
                } else {
                    log.warn("❌ [WebSocket] Gateway authentication missing X-User-Id");
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }
            }

            // Validate token
            if (!jwtUtil.validateToken(token)) {
                log.warn("❌ [WebSocket] Invalid token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Extract user info from token
            String userId = jwtUtil.extractUserId(token);
            String userEmail = jwtUtil.extractEmail(token);
            String userRole = jwtUtil.extractRole(token);

            if (userId == null) {
                log.warn("❌ [WebSocket] Token missing userId (subject)");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // Store in session attributes
            attributes.put("userId", userId);
            attributes.put("userEmail", userEmail != null ? userEmail : "unknown");
            attributes.put("userRole", userRole != null ? userRole : "USER");
            attributes.put("token", token);

            log.info("🎉 ✅ [WebSocket] Handshake authenticated: userId={}, email={}, role={}",
                    userId, userEmail, userRole);

            return true;

        } catch (Exception e) {
            log.error("❌ [WebSocket] Handshake failed: {}", e.getMessage(), e);
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
            log.error("❌ [WebSocket] After handshake error: {}", exception.getMessage());
        } else {
            log.debug("✅ [WebSocket] Handshake completed successfully");
        }
    }

    /**
     * Extract JWT token from request
     * Priority: 1) Query param, 2) Authorization header, 3) Gateway headers
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Check query parameter (frontend sends token here)
        String query = request.getURI().getQuery();
        if (query != null && query.contains("access_token=")) {
            String token = extractQueryParam(query, "access_token");
            if (token != null && !token.isEmpty()) {
                log.debug("🔑 [WebSocket] Token found in query parameter (length: {})", token.length());
                return token;
            }
        }

        // 2. Check Authorization header (gateway forwards token here)
        String authHeader = extractHeader(request, "Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            log.debug("🔑 [WebSocket] Token found in Authorization header (length: {})", token.length());
            return token;
        }

        // 3. Check if pre-authenticated by gateway
        String userId = extractHeader(request, "X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            log.debug("🔑 [WebSocket] Pre-authenticated by API Gateway (userId: {})", userId);
            return "gateway-authenticated";
        }

        log.warn("❌ [WebSocket] No token found in any location");
        return null;
    }

    /**
     * Extract header value from request
     */
    private String extractHeader(ServerHttpRequest request, String headerName) {
        List<String> headers = request.getHeaders().get(headerName);
        if (headers != null && !headers.isEmpty()) {
            return headers.get(0);
        }
        return null;
    }

    /**
     * Extract parameter from query string
     */
    private String extractQueryParam(String query, String paramName) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}