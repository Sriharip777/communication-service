package com.tcon.communication_service.whiteboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcon.communication_service.config.AgoraWhiteboardConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Agora Interactive Whiteboard API Client
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraWhiteboardClient {

    private final AgoraWhiteboardConfig config;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * ✅ FIXED: Generate SDK Token reactively (returns Mono)
     */
    public Mono<String> generateSdkToken() {
        WebClient webClient = webClientBuilder
                .baseUrl(config.getApiBaseUrl())
                .build();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("accessKey", config.getAccessKey());
        requestBody.put("secretAccessKey", config.getSecretKey());
        requestBody.put("lifespan", config.getTokenExpiryHours() * 3600 * 1000L);
        requestBody.put("role", "admin");

        log.info("🔑 Generating SDK token via Agora API...");
        log.debug("📤 Request body: accessKey={}, lifespan={}, role=admin",
                config.getAccessKey().substring(0, Math.min(8, config.getAccessKey().length())),
                config.getTokenExpiryHours() * 3600 * 1000L);

        return webClient.post()
                .uri("/tokens/teams")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("region", config.getRegion())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    log.info("✅ SDK Token generated successfully");
                    log.debug("📥 SDK Token response: {}", response);
                    return response.replaceAll("^\"|\"$", ""); // Remove quotes
                })
                .doOnError(error -> log.error("❌ Failed to generate SDK token via API: {}", error.getMessage(), error));
    }

    /**
     * Create a new whiteboard room
     */
    public Mono<Map<String, Object>> createRoom(Integer limit) {
        return generateSdkToken()
                .flatMap(sdkToken -> {
                    WebClient webClient = webClientBuilder
                            .baseUrl(config.getApiBaseUrl())
                            .build();

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("isRecord", false);
                    if (limit != null && limit > 0) {
                        requestBody.put("limit", limit);
                    } else {
                        requestBody.put("limit", 50);
                    }

                    log.info("🚀 Creating Agora whiteboard room...");

                    return webClient.post()
                            .uri("/rooms")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .header("token", sdkToken)
                            .header("region", config.getRegion())
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(response -> {
                                try {
                                    log.debug("📥 Room creation response: {}", response);
                                    JsonNode node = objectMapper.readTree(response);
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("uuid", node.get("uuid").asText());
                                    result.put("teamUUID", node.get("teamUUID").asText());
                                    result.put("appUUID", node.get("appUUID").asText());
                                    result.put("isBan", node.get("isBan").asBoolean());
                                    result.put("createdAt", node.get("createdAt").asText());

                                    log.info("✅ Created Agora whiteboard room: {}", result.get("uuid"));
                                    return result;
                                } catch (Exception e) {
                                    log.error("❌ Failed to parse room creation response", e);
                                    throw new RuntimeException("Failed to parse room creation response", e);
                                }
                            });
                })
                .doOnError(error -> log.error("❌ Failed to create whiteboard room: {}", error.getMessage()));
    }

    /**
     * Generate room token for joining
     */
    public Mono<String> generateRoomToken(String uuid, String role) {
        return generateSdkToken()
                .flatMap(sdkToken -> {
                    WebClient webClient = webClientBuilder
                            .baseUrl(config.getApiBaseUrl())
                            .build();

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("lifespan", config.getTokenExpiryHours() * 3600 * 1000L);
                    requestBody.put("role", role);

                    log.info("🔑 Generating room token for uuid: {} with role: {}", uuid, role);

                    return webClient.post()
                            .uri("/tokens/rooms/" + uuid)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .header("token", sdkToken)
                            .header("region", config.getRegion())
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(response -> {
                                log.info("✅ Generated room token for uuid: {}", uuid);
                                return response.replaceAll("^\"|\"$", "");
                            });
                })
                .doOnError(error -> log.error("❌ Failed to generate room token: {}", error.getMessage()));
    }

    /**
     * Get room information
     */
    public Mono<Map<String, Object>> getRoomInfo(String uuid) {
        return generateSdkToken()
                .flatMap(sdkToken -> {
                    WebClient webClient = webClientBuilder
                            .baseUrl(config.getApiBaseUrl())
                            .build();

                    log.info("📋 Getting room info for uuid: {}", uuid);

                    return webClient.get()
                            .uri("/rooms/" + uuid)
                            .header("token", sdkToken)
                            .header("region", config.getRegion())
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(response -> {
                                try {
                                    log.debug("📥 Room info response: {}", response);
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> result = objectMapper.readValue(response, Map.class);
                                    return result;
                                } catch (Exception e) {
                                    log.error("❌ Failed to parse room info", e);
                                    throw new RuntimeException("Failed to parse room info", e);
                                }
                            });
                })
                .doOnError(error -> log.error("❌ Failed to get room info: {}", error.getMessage()));
    }

    /**
     * Ban/Close a whiteboard room
     */
    public Mono<Void> banRoom(String uuid) {
        return generateSdkToken()
                .flatMap(sdkToken -> {
                    WebClient webClient = webClientBuilder
                            .baseUrl(config.getApiBaseUrl())
                            .build();

                    log.info("🚫 Banning whiteboard room: {}", uuid);

                    return webClient.patch()
                            .uri("/rooms/" + uuid)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .header("token", sdkToken)
                            .header("region", config.getRegion())
                            .bodyValue(Map.of("isBan", true))
                            .retrieve()
                            .bodyToMono(Void.class)
                            .doOnSuccess(v -> log.info("✅ Banned whiteboard room: {}", uuid));
                })
                .doOnError(error -> log.error("❌ Failed to ban room: {}", error.getMessage()));
    }

    /**
     * Synchronous version - Create room (for non-reactive contexts only)
     */
    public String createRoomSync() {
        Map<String, Object> result = createRoom(50).block();
        if (result != null && result.containsKey("uuid")) {
            return result.get("uuid").toString();
        }
        throw new RuntimeException("Failed to create whiteboard room");
    }

    /**
     * Synchronous version - Generate room token (for non-reactive contexts only)
     */
    public String generateRoomTokenSync(String uuid, String userId, String role, long lifespan) {
        return generateRoomToken(uuid, role).block();
    }
}