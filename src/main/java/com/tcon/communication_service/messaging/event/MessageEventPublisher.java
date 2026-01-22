package com.tcon.communication_service.messaging.event;

import com.tcon.communication_service.messaging.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Message Event Publisher
 * Publishes message events to Kafka
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.message-events}")
    private String topicName;

    public void publishMessageSent(Message message) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "MESSAGE_SENT");
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversationId());
        event.put("senderId", message.getSenderId());
        event.put("receiverId", message.getReceiverId());
        event.put("messageType", message.getType());
        event.put("timestamp", java.time.Instant.now());

        publish(event);
    }

    public void publishMessageDelivered(Message message) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "MESSAGE_DELIVERED");
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversationId());
        event.put("timestamp", java.time.Instant.now());

        publish(event);
    }

    public void publishMessageRead(Message message) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "MESSAGE_READ");
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversationId());
        event.put("readAt", message.getReadAt());
        event.put("timestamp", java.time.Instant.now());

        publish(event);
    }

    private void publish(Map<String, Object> event) {
        try {
            kafkaTemplate.send(topicName, event);
            log.info("Published message event: {}", event.get("eventType"));
        } catch (Exception e) {
            log.error("Error publishing message event: {}", e.getMessage(), e);
        }
    }
}
