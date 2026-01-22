package com.tcon.communication_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Configuration
 * Configuration for Kafka topics and settings
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topics.video-session-events}")
    private String videoSessionEventsTopic;

    @Value("${spring.kafka.topics.message-events}")
    private String messageEventsTopic;

    @Value("${spring.kafka.topics.class-events}")
    private String classEventsTopic;

    @Value("${spring.kafka.topics.booking-events}")
    private String bookingEventsTopic;

    @Bean
    public NewTopic videoSessionEventsTopic() {
        return TopicBuilder.name(videoSessionEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic messageEventsTopic() {
        return TopicBuilder.name(messageEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic classEventsTopic() {
        return TopicBuilder.name(classEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(bookingEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
