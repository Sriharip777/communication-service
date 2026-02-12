package com.tcon.communication_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Get signing key from secret
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())  // ✅ JJWT 0.12.x syntax
                    .build()
                    .parseSignedClaims(token);    // ✅ Changed from parseClaimsJws
            log.debug("✅ Token validated successfully");
            return true;
        } catch (Exception e) {
            log.error("❌ Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract user ID (subject) from token
     */
    public String extractUserId(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            log.error("❌ Failed to extract userId: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract email from token
     */
    public String extractEmail(String token) {
        try {
            Object email = getClaims(token).get("email");
            return email != null ? email.toString() : null;
        } catch (Exception e) {
            log.error("❌ Failed to extract email: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract role from token
     */
    public String extractRole(String token) {
        try {
            Object role = getClaims(token).get("role");
            return role != null ? role.toString() : null;
        } catch (Exception e) {
            log.error("❌ Failed to extract role: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get all claims from token
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())     // ✅ JJWT 0.12.x syntax
                .build()
                .parseSignedClaims(token)        // ✅ Changed from parseClaimsJws
                .getPayload();                   // ✅ Changed from getBody()
    }
}