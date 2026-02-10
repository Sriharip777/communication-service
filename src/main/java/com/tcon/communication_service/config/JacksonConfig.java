package com.tcon.communication_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.info("🔧 Configuring Jackson ObjectMapper with JavaTimeModule");

        ObjectMapper mapper = new ObjectMapper();

        // Register JSR310 (Java 8 Date/Time) module
        mapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Fail on empty beans (helps catch serialization issues)
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        log.info("✅ Jackson ObjectMapper configured successfully");
        return mapper;
    }
}
