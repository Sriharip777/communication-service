package com.tcon.communication_service.messaging.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Enrollment Validation Service
 * Validates if users can communicate based on course enrollments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentValidationService {

    private final WebClient.Builder webClientBuilder;

    /**
     * Check if two users can communicate via learning-management-service
     */
    public boolean canUsersCommunicate(String user1, String user2) {
        log.debug("Checking enrollment relationship between {} and {}", user1, user2);

        try {
            Boolean canCommunicate = webClientBuilder.build()
                    .get()
                    .uri("http://learning-management-service/api/courses/can-communicate",
                            uriBuilder -> uriBuilder
                                    .queryParam("user1", user1)
                                    .queryParam("user2", user2)
                                    .build())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .onErrorResume(e -> {
                        log.error("Error calling learning-management-service: {}", e.getMessage());
                        return Mono.just(false);
                    })
                    .block();

            return Boolean.TRUE.equals(canCommunicate);

        } catch (Exception e) {
            log.error("Failed to validate enrollment relationship: {}", e.getMessage(), e);
            return false;
        }
    }
}
