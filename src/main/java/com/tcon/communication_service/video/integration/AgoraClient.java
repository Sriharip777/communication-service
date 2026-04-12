package com.tcon.communication_service.video.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcon.communication_service.video.dto.AgoraTokenResponse;
import io.agora.media.RtcTokenBuilder;
import io.agora.media.RtcTokenBuilder.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@Service
public class AgoraClient {

    private final String appId;
    private final String appCertificate;
    private final String customerId;
    private final String customerSecret;
    private final int storageVendor;
    private final int storageRegion;
    private final String storageBucket;
    private final String storageAccessKey;
    private final String storageSecretKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ✅ Fixed UID used by the Agora recording bot
    private static final int RECORDING_UID = 12345;

    public AgoraClient(
            @Value("${agora.api.app-id}") String appId,
            @Value("${agora.api.app-certificate}") String appCertificate,
            @Value("${agora.api.customer-id:}") String customerId,
            @Value("${agora.api.customer-secret:}") String customerSecret,
            @Value("${agora.storage.vendor:6}") int storageVendor,
            @Value("${agora.storage.region:8}") int storageRegion,
            @Value("${agora.storage.bucket:}") String storageBucket,
            @Value("${agora.storage.access-key:}") String storageAccessKey,
            @Value("${agora.storage.secret-key:}") String storageSecretKey
    ) {
        this.appId = appId;
        this.appCertificate = appCertificate;
        this.customerId = customerId;
        this.customerSecret = customerSecret;
        this.storageVendor = storageVendor;
        this.storageRegion = storageRegion;
        this.storageBucket = storageBucket;
        this.storageAccessKey = storageAccessKey;
        this.storageSecretKey = storageSecretKey;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ─── CREATE ROOM (unchanged) ──────────────────────────────────────────

    public String createRoom(String roomName, int durationMinutes) {
        log.info("Creating Agora channel: {}", roomName);
        String channelName = sanitizeChannelName(roomName);
        log.info("Agora channel ready: {}", channelName);
        log.debug("Duration: {} minutes", durationMinutes);
        return channelName;
    }

    // ─── GENERATE AUTH TOKEN (unchanged) ─────────────────────────────────

    public AgoraTokenResponse generateAuthToken(
            String channelName, String userId, String roleStr) {

        log.info("Generating Agora token for user={} role={} channel={}",
                userId, roleStr, channelName);

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

            log.info("Agora token generated — User={}, UID={}, Channel={}",
                    userId, uid, channelName);

            return AgoraTokenResponse.builder()
                    .token(token)
                    .uid(uid)
                    .channelName(channelName)
                    .expiresAt(privilegeExpiredTs * 1000L)
                    .build();

        } catch (Exception e) {
            log.error("Error generating Agora token for user={} channel={}",
                    userId, channelName, e);
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR, "Failed to generate Agora token");
        }
    }

    // ─── CLOUD RECORDING: START ───────────────────────────────────────────

    /**
     * Starts Agora Cloud Recording.
     * Returns "resourceId:sid" — store this in VideoSession.recordingId.
     * If cloud recording is not configured, returns a stub ID.
     */
    public String startRecording(String channelName) {
        log.info("Starting cloud recording for channel: {}", channelName);

        if (!isCloudRecordingConfigured()) {
            log.warn("Cloud recording not fully configured. Using stub recordingId.");
            return "agora_rec_" + UUID.randomUUID();
        }

        try {
            // ── Step 1: Acquire resource ──────────────────────────────────
            String resourceId = acquireResource(channelName);
            if (resourceId == null) {
                log.warn("Failed to acquire resourceId. Using stub.");
                return "agora_rec_" + UUID.randomUUID();
            }

            // ── Step 2: Generate token for recorder bot ───────────────────
            int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
            int privilegeExpiredTs = currentTimestamp + 86400;
            RtcTokenBuilder tokenBuilder = new RtcTokenBuilder();
            String recToken = tokenBuilder.buildTokenWithUid(
                    appId,
                    appCertificate,
                    channelName,
                    RECORDING_UID,
                    Role.Role_Publisher,
                    privilegeExpiredTs
            );

            // ── Step 3: Start cloud recording ─────────────────────────────
            String url = "https://api.agora.io/v1/apps/" + appId
                    + "/cloud_recording/resourceid/" + resourceId
                    + "/mode/mix/start";

            // Storage config — GCS
            Map<String, Object> storageConfig = new HashMap<>();
            storageConfig.put("vendor", storageVendor);       // 6 = GCS
            storageConfig.put("region", storageRegion);       // 8 = Mumbai
            storageConfig.put("bucket", storageBucket);       // tutoring-platform-files
            storageConfig.put("accessKey", storageAccessKey); // HMAC access key
            storageConfig.put("secretKey", storageSecretKey); // HMAC secret key
            storageConfig.put("fileNamePrefix",
                    List.of("recordings", channelName));

            // Video transcoding config
            Map<String, Object> transcodingConfig = new HashMap<>();
            transcodingConfig.put("width", 1280);
            transcodingConfig.put("height", 720);
            transcodingConfig.put("bitrate", 2260);
            transcodingConfig.put("fps", 15);
            transcodingConfig.put("mixedVideoLayout", 1);

            // Recording config
            Map<String, Object> recordingConfig = new HashMap<>();
            recordingConfig.put("maxIdleTime", 30);
            recordingConfig.put("streamTypes", 2);    // 2 = audio + video
            recordingConfig.put("channelType", 0);    // 0 = communication
            recordingConfig.put("transcodingConfig", transcodingConfig);

            // Client request
            Map<String, Object> clientRequest = new HashMap<>();
            clientRequest.put("token", recToken);
            clientRequest.put("storageConfig", storageConfig);
            clientRequest.put("recordingConfig", recordingConfig);
            clientRequest.put("recordingFileConfig", Map.of(
                    "avFileType", List.of("hls", "mp4")
            ));

            // Full body
            Map<String, Object> body = new HashMap<>();
            body.put("cname", channelName);
            body.put("uid", String.valueOf(RECORDING_UID));
            body.put("clientRequest", clientRequest);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class
            );

            log.info("Agora start response: {}", response.getBody());

            JsonNode json = objectMapper.readTree(response.getBody());
            String sid = json.path("sid").asText(null);

            if (sid == null || sid.isBlank()) {
                log.error("No sid in Agora start response: {}", response.getBody());
                return "agora_rec_" + UUID.randomUUID();
            }

            // ✅ Store as "resourceId:sid" so stopRecording can parse both
            String recordingId = resourceId + ":" + sid;
            log.info("Cloud recording started: recordingId={} channel={}",
                    recordingId, channelName);
            return recordingId;

        } catch (Exception e) {
            log.error("Failed to start cloud recording for channel={}: {}",
                    channelName, e.getMessage(), e);
            return "agora_rec_" + UUID.randomUUID();
        }
    }

    // ─── CLOUD RECORDING: STOP ────────────────────────────────────────────

    /**
     * Stops Agora Cloud Recording.
     * Returns the real GCS video URL, or null if unavailable.
     * VideoSessionService uses this URL to save in MongoDB.
     */
    // ─── CLOUD RECORDING: STOP ────────────────────────────────────────────

    /**
     * ✅ FIX: Added channelName parameter — required by Agora stop API
     */
    public String stopRecording(String recordingId, String channelName) {
        log.info("Stopping cloud recording: recordingId={} channel={}",
                recordingId, channelName);

        if (!isCloudRecordingConfigured()
                || recordingId == null
                || !recordingId.contains(":")) {
            log.warn("Not a real recording or not configured. " +
                    "Skipping stop. recordingId={}", recordingId);
            return null;
        }

        try {
            String[] parts = recordingId.split(":", 2);
            String resourceId = parts[0];
            String sid = parts[1];

            String url = "https://api.agora.io/v1/apps/" + appId
                    + "/cloud_recording/resourceid/" + resourceId
                    + "/sid/" + sid
                    + "/mode/mix/stop";

            Map<String, Object> clientRequest = new HashMap<>();
            clientRequest.put("async_stop", false);

            Map<String, Object> body = new HashMap<>();
            body.put("cname", channelName);  // ✅ FIX: Real channel name
            body.put("uid", String.valueOf(RECORDING_UID));
            body.put("clientRequest", clientRequest);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class
            );

            log.info("Agora stop response: {}", response.getBody());

            JsonNode json = objectMapper.readTree(response.getBody());
            JsonNode fileList = json
                    .path("serverResponse")
                    .path("fileList");

            // ✅ Fixed — checks .mp4 first, falls back to .m3u8
            if (fileList.isArray()) {
                String mp4Url = null;
                String m3u8Url = null;

                for (JsonNode file : fileList) {
                    String fileName = file.path("fileName").asText("");
                    if (fileName.endsWith(".mp4")) {
                        mp4Url = "https://storage.googleapis.com/"
                                + storageBucket + "/" + fileName;
                        log.info("Found MP4 recording: {}", mp4Url);
                    } else if (fileName.endsWith(".m3u8")) {
                        m3u8Url = "https://storage.googleapis.com/"
                                + storageBucket + "/" + fileName;
                        log.info("Found M3U8 recording: {}", m3u8Url);
                    }
                }

                // ✅ Prefer MP4, fall back to M3U8
                if (mp4Url != null) {
                    log.info("Recording stopped. Using MP4 URL: {}", mp4Url);
                    return mp4Url;
                }
                if (m3u8Url != null) {
                    log.info("Recording stopped. Using M3U8 URL: {}", m3u8Url);
                    return m3u8Url;
                }
            }

            log.warn("No MP4 file found in Agora stop response for recordingId={}",
                    recordingId);
            return null;

        } catch (Exception e) {
            log.error("Failed to stop cloud recording: recordingId={} error={}",
                    recordingId, e.getMessage(), e);
            return null;
        }
    }

    // ─── PRIVATE: ACQUIRE RESOURCE ───────────────────────────────────────

    private String acquireResource(String channelName) {
        try {
            String url = "https://api.agora.io/v1/apps/" + appId
                    + "/cloud_recording/acquire";

            Map<String, Object> clientRequest = new HashMap<>();
            clientRequest.put("resourceExpiredHour", 24);
            clientRequest.put("scene", 0);

            Map<String, Object> body = new HashMap<>();
            body.put("cname", channelName);
            body.put("uid", String.valueOf(RECORDING_UID));
            body.put("clientRequest", clientRequest);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class
            );

            log.info("Agora acquire response: {}", response.getBody());

            JsonNode json = objectMapper.readTree(response.getBody());
            String resourceId = json.path("resourceId").asText(null);

            if (resourceId == null || resourceId.isBlank()) {
                log.error("No resourceId in Agora acquire response: {}",
                        response.getBody());
                return null;
            }

            log.info("Resource acquired: resourceId={} channel={}",
                    resourceId, channelName);
            return resourceId;

        } catch (Exception e) {
            log.error("Failed to acquire Agora resource for channel={}: {}",
                    channelName, e.getMessage(), e);
            return null;
        }
    }

    // ─── PRIVATE: BUILD HTTP HEADERS ─────────────────────────────────────

    private HttpHeaders buildHeaders() {
        // ✅ Agora Cloud Recording uses Basic Auth with customerId:customerSecret
        String credentials = customerId + ":" + customerSecret;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    // ─── PRIVATE: IS CLOUD RECORDING CONFIGURED ──────────────────────────

    private boolean isCloudRecordingConfigured() {
        boolean configured = customerId != null && !customerId.isBlank()
                && customerSecret != null && !customerSecret.isBlank()
                && storageBucket != null && !storageBucket.isBlank()
                && storageAccessKey != null && !storageAccessKey.isBlank()
                && storageSecretKey != null && !storageSecretKey.isBlank();

        if (!configured) {
            log.warn("Cloud recording config missing — " +
                            "customerId={}, bucket={}, accessKey={}",
                    customerId != null ? "set" : "MISSING",
                    storageBucket != null ? storageBucket : "MISSING",
                    storageAccessKey != null ? "set" : "MISSING");
        }

        return configured;
    }

    // ─── PRIVATE: GENERATE CONSISTENT UID ────────────────────────────────

    private int generateConsistentUid(String userId) {
        int uid = Math.abs(userId.hashCode());
        if (uid <= 0) {
            uid = Math.abs(uid) + 1;
        }
        return uid;
    }

    // ─── PRIVATE: DETERMINE AGORA ROLE ───────────────────────────────────

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

    // ─── PRIVATE: SANITIZE CHANNEL NAME ──────────────────────────────────

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

    // ─── PUBLIC: GET APP ID ───────────────────────────────────────────────

    public String getAppId() {
        return appId;
    }
}