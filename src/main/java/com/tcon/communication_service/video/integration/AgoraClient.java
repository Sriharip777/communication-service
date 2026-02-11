package com.tcon.communication_service.video.integration;

import com.tcon.communication_service.video.dto.AgoraTokenResponse;
import io.agora.media.RtcTokenBuilder;
import io.agora.media.RtcTokenBuilder.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agora RTC Client
 * Handles Agora video session integration
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Service
public class AgoraClient {

    @Value("${agora.api.app-id}")
    private String appId;

    @Value("${agora.api.app-certificate}")
    private String appCertificate;

    /**
     * Create a channel/room
     * In Agora, channels are created automatically when first user joins
     */
    public String createRoom(String roomName, int durationMinutes) {
        log.info("Creating Agora channel: {}", roomName);

        // Sanitize channel name to meet Agora requirements
        String channelName = sanitizeChannelName(roomName);

        log.info("Agora channel ready: {}", channelName);
        log.debug("Channel will auto-create when first user joins");
        log.debug("Duration: {} minutes", durationMinutes);

        return channelName;
    }

    /**
     * Generate RTC auth token for joining a channel
     * Returns both token and UID for secure channel joining
     */
    /**
     * Generate RTC auth token for joining a channel
     * Returns both token and UID for secure channel joining
     */
    public AgoraTokenResponse generateAuthToken(String channelName, String userId, String roleStr) {
        log.info("Generating Agora token for user {} with role {} in channel {}",
                userId, roleStr, channelName);

        try {
            // Convert role string to Agora Role
            Role role = determineAgoraRole(roleStr);

            // Token expiration time (24 hours from now)
            int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
            int privilegeExpiredTs = currentTimestamp + 86400;

            // ✅ CONSISTENT UID: Always generate same UID for same user
            // This allows seamless rejoin even after disconnection
            int uid = generateConsistentUid(userId);

            log.debug("Token parameters - Channel: {}, UID: {}, Role: {}, Expiry: {}",
                    channelName, uid, role, privilegeExpiredTs);

            // Create an instance of RtcTokenBuilder
            RtcTokenBuilder tokenBuilder = new RtcTokenBuilder();

            // Generate token using the instance method
            String token = tokenBuilder.buildTokenWithUid(
                    appId,
                    appCertificate,
                    channelName,
                    uid,
                    role,
                    privilegeExpiredTs
            );

            log.info("✅ Agora token generated - User: {}, UID: {}, Channel: {}",
                    userId, uid, channelName);
            log.debug("Token (first 20 chars): {}...", token.substring(0, Math.min(20, token.length())));

            // Return AgoraTokenResponse with token and UID
            return AgoraTokenResponse.builder()
                    .token(token)
                    .uid(uid)
                    .channelName(channelName)
                    .expiresAt(privilegeExpiredTs * 1000L)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error generating Agora token for user {} in channel {}: {}",
                    userId, channelName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate Agora token", e);
        }
    }

    /**
     * Generate consistent UID for a user
     * Same user will always get the same UID, enabling seamless rejoin
     */
    private int generateConsistentUid(String userId) {
        // Use hashCode to generate consistent UID
        int uid = Math.abs(userId.hashCode());

        // Ensure UID is within valid range (positive 32-bit integer)
        // Max UID for Agora: 2^32 - 1
        if (uid <= 0) {
            uid = Math.abs(uid) + 1;
        }

        log.debug("Generated consistent UID: {} for userId: {}", uid, userId);
        return uid;
    }



    /**
     * Start cloud recording
     * Note: This is a simplified version. Full implementation requires Agora Cloud Recording REST API
     */
    public String startRecording(String channelName) {
        log.info("Starting recording for channel: {}", channelName);

        // Generate recording resource ID
        String recordingId = "agora_rec_" + UUID.randomUUID().toString();

        log.warn("⚠️  Recording functionality requires Agora Cloud Recording REST API setup");
        log.info("Recording ID generated: {}", recordingId);
        log.info("To enable full recording: https://docs.agora.io/en/cloud-recording/");

        return recordingId;
    }

    /**
     * Stop cloud recording
     */
    public void stopRecording(String recordingId) {
        log.info("Stopping recording: {}", recordingId);

        log.warn("⚠️  Recording stop requires Agora Cloud Recording REST API");
        log.info("Recording stopped (placeholder): {}", recordingId);
    }

    /**
     * Determine Agora role based on user role string
     */
    private Role determineAgoraRole(String roleStr) {
        if (roleStr == null) {
            log.warn("Role string is null, defaulting to Publisher");
            return Role.Role_Publisher;
        }

        return switch (roleStr.toUpperCase()) {
            case "TEACHER", "STUDENT" -> {
                log.debug("Assigning Publisher role for: {}", roleStr);
                yield Role.Role_Publisher;  // Can publish audio/video
            }
            case "PARENT_OBSERVER", "OBSERVER" -> {
                log.debug("Assigning Subscriber role for: {}", roleStr);
                yield Role.Role_Subscriber;  // View only
            }
            default -> {
                log.warn("Unknown role: {}, defaulting to Publisher", roleStr);
                yield Role.Role_Publisher;
            }
        };
    }

    /**
     * Sanitize channel name to meet Agora requirements
     * - ASCII letters, numbers, underscores, hyphens
     * - Max 64 characters
     * - Cannot start with special characters
     */
    private String sanitizeChannelName(String roomName) {
        if (roomName == null || roomName.isEmpty()) {
            log.warn("Room name is empty, generating random name");
            return "channel_" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Remove special characters, keep only alphanumeric, underscore, hyphen
        String sanitized = roomName
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("^[^a-zA-Z0-9]+", "")  // Remove leading special chars
                .toLowerCase();

        // Limit to 64 characters
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }

        // Ensure not empty after sanitization
        if (sanitized.isEmpty()) {
            sanitized = "channel_" + UUID.randomUUID().toString().substring(0, 8);
        }

        log.debug("Sanitized channel name: {} -> {}", roomName, sanitized);
        return sanitized;
    }

    /**
     * Get App ID (for client-side initialization)
     */
    public String getAppId() {
        return appId;
    }
}
