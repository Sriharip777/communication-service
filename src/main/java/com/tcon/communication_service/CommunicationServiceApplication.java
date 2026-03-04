package com.tcon.communication_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Communication Service Application
 * Handles video sessions, messaging, and whiteboard functionality
 *
 * @author Senior Developer
 * @version 1.0.0
 * @since 2026-01-20
 */
@SpringBootApplication(exclude = {HttpClientAutoConfiguration.class})  // ✅ ADD THIS
@EnableDiscoveryClient
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.tcon.communication_service")
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableFeignClients
public class CommunicationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunicationServiceApplication.class, args);
    }
}