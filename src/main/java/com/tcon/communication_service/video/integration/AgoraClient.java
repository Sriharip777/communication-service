package com.tcon.communication_service.video.integration;

import com.tcon.communication_service.video.dto.AgoraTokenResponse;
import io.agora.media.RtcTokenBuilder;
import io.agora.media.RtcTokenBuilder.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@Service
public class AgoraClient {

    private final String appId;
    private final String appCertificate;

    public AgoraClient(
            @Value("${agora.api.app-id}") String appId,
            @Value("${agora.api.app-certificate}") String appCertificate
    ) {
        this.appId = appId;
        this.appCertificate = appCertificate;
    }

    public String createRoom(String roomName, int durationMinutes) {
        log.info("Creating Agora channel: {}", roomName);
        String channelName = sanitizeChannelName(roomName);
        log.info("Agora channel ready: {}", channelName);
        log.debug("Duration: {} minutes", durationMinutes);
        return channelName;
    }

    public AgoraTokenResponse generateAuthToken(String channelName, String userId, String roleStr) {
        log.info("Generating Agora token for user {} with role {} in channel {}", userId, roleStr, channelName);

        try {
            Role role = determineAgoraRole(roleStr);
            int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
            int privilegeExpiredTs = currentTimestamp + 86400;
            int uid = generateConsistentUid(userId);

            RtcTokenBuilder tokenBuilder = new RtcTokenBuilder();
            String token = tokenBuilder.buildTokenWithUid(
                    appId,
                    appCertificate,
                    channelName,
                    uid,
                    role,
                    privilegeExpiredTs
            );

            log.info("Agora token generated - User: {}, UID: {}, Channel: {}", userId, uid, channelName);

            return AgoraTokenResponse.builder()
                    .token(token)
                    .uid(uid)
                    .channelName(channelName)
                    .expiresAt(privilegeExpiredTs * 1000L)
                    .build();

        } catch (Exception e) {
            log.error("Error generating Agora token for user {} in channel {}", userId, channelName, e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to generate Agora token");
        }
    }

    private int generateConsistentUid(String userId) {
        int uid = Math.abs(userId.hashCode());
        if (uid <= 0) {
            uid = Math.abs(uid) + 1;
        }
        return uid;
    }

    public String startRecording(String channelName) {
        log.info("Starting recording for channel: {}", channelName);
        String recordingId = "agora_rec_" + UUID.randomUUID();
        log.warn("Recording functionality requires Agora Cloud Recording REST API setup");
        return recordingId;
    }

    public void stopRecording(String recordingId) {
        log.info("Stopping recording: {}", recordingId);
        log.warn("Recording stop requires Agora Cloud Recording REST API");
    }

    private Role determineAgoraRole(String roleStr) {
        if (roleStr == null) {
            return Role.Role_Publisher;
        }

        return switch (roleStr.toUpperCase()) {
            case "TEACHER", "STUDENT" -> Role.Role_Publisher;
            case "PARENT_OBSERVER", "OBSERVER" -> Role.Role_Subscriber;
            default -> Role.Role_Publisher;
        };
    }

    private String sanitizeChannelName(String roomName) {
        if (roomName == null || roomName.isBlank()) {
            return "channel_" + UUID.randomUUID().toString().substring(0, 8);
        }

        String sanitized = roomName
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("^[^a-zA-Z0-9]+", "")
                .toLowerCase();

        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }

        if (sanitized.isEmpty()) {
            sanitized = "channel_" + UUID.randomUUID().toString().substring(0, 8);
        }

        return sanitized;
    }

    public String getAppId() {
        return appId;
    }
}