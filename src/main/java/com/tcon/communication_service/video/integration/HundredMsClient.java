package com.tcon.communication_service.video.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 100ms Client
 * Integration with 100ms Video API
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HundredMsClient {

    private final WebClient.Builder webClientBuilder;  // ← Injected from WebClientConfig
    private final ObjectMapper objectMapper;

    @Value("${hundredms.api.base-url}")
    private String baseUrl;

    @Value("${hundredms.api.management-token}")
    private String managementToken;

    @Value("${hundredms.api.app-access-key}")
    private String appAccessKey;

    @Value("${hundredms.api.app-secret}")
    private String appSecret;

    @Value("${hundredms.templates.default-template-id}")
    private String defaultTemplateId;

    /**
     * Create a new room
     */
    public String createRoom(String roomName, int durationMinutes) {
        log.info("Creating 100ms room: {}", roomName);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", roomName);
        requestBody.put("description", "Tutoring session room");
        requestBody.put("template_id", defaultTemplateId);
        requestBody.put("region", "in");

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            String response = webClient.post()
                    .uri("/rooms")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + managementToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            String roomId = jsonNode.get("id").asText();

            log.info("Room created successfully: {}", roomId);
            return roomId;

        } catch (Exception e) {
            log.error("Error creating room: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create 100ms room", e);
        }
    }

    /**
     * Generate auth token for joining a room
     */
    public String generateAuthToken(String roomId, String userId, String role) {
        log.info("Generating auth token for user {} in room {}", userId, roomId);

        try {
            long nowMillis = System.currentTimeMillis();
            Date now = new Date(nowMillis);
            Date expiration = new Date(nowMillis + (24 * 60 * 60 * 1000)); // 24 hours

            Map<String, Object> claims = new HashMap<>();
            claims.put("access_key", appAccessKey);
            claims.put("room_id", roomId);
            claims.put("user_id", userId);
            claims.put("role", role.toLowerCase());
            claims.put("type", "app");
            claims.put("version", 2);

            SecretKey key = Keys.hmacShaKeyFor(appSecret.getBytes());

            String token = Jwts.builder()
                    .setClaims(claims)
                    .setSubject(userId)
                    .setIssuedAt(now)
                    .setExpiration(expiration)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            log.info("Auth token generated successfully for user: {}", userId);
            return token;

        } catch (Exception e) {
            log.error("Error generating auth token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate auth token", e);
        }
    }

    /**
     * Start recording
     */
    public String startRecording(String roomId) {
        log.info("Starting recording for room: {}", roomId);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("room_id", roomId);

        Map<String, Object> meetingUrl = new HashMap<>();
        meetingUrl.put("meeting_url", baseUrl + "/meeting/" + roomId);
        requestBody.put("meeting_url", meetingUrl);

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            String response = webClient.post()
                    .uri("/recordings/room/" + roomId + "/start")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + managementToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonNode = objectMapper.readTree(response);
            String recordingId = jsonNode.get("id").asText();

            log.info("Recording started successfully: {}", recordingId);
            return recordingId;

        } catch (Exception e) {
            log.error("Error starting recording: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start recording", e);
        }
    }

    /**
     * Stop recording
     */
    public void stopRecording(String recordingId) {
        log.info("Stopping recording: {}", recordingId);

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            webClient.post()
                    .uri("/recordings/" + recordingId + "/stop")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + managementToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Recording stopped successfully: {}", recordingId);

        } catch (Exception e) {
            log.error("Error stopping recording: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop recording", e);
        }
    }

    /**
     * Get recording details
     */
    public Map<String, Object> getRecording(String recordingId) {
        log.info("Getting recording details: {}", recordingId);

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            String response = webClient.get()
                    .uri("/recordings/" + recordingId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + managementToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(response, Map.class);

        } catch (Exception e) {
            log.error("Error getting recording: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get recording details", e);
        }
    }

    /**
     * Delete room
     */
    public void deleteRoom(String roomId) {
        log.info("Deleting room: {}", roomId);

        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            webClient.delete()
                    .uri("/rooms/" + roomId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + managementToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Room deleted successfully: {}", roomId);

        } catch (Exception e) {
            log.error("Error deleting room: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete room", e);
        }
    }
}
