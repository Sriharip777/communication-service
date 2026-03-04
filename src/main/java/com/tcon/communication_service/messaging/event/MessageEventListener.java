package com.tcon.communication_service.messaging.event;

import com.tcon.communication_service.messaging.entity.Message;
import com.tcon.communication_service.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Message Event Listener
 * Bridges Kafka message events to WebSocket notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;

    @KafkaListener(
            topics = "${spring.kafka.topics.message-events}",
            groupId = "ws-message-events"
    )
    public void handleMessageEvent(Map<String, Object> event) {
        if (event == null || event.get("eventType") == null) {
            return;
        }

        String type = (String) event.get("eventType");
        log.info("Kafka event received: {}", event);

        if (!"MESSAGE_READ".equals(type)) {
            return; // ignore SENT/DELIVERED
        }

        String messageId = (String) event.get("messageId");
        String conversationId = (String) event.get("conversationId");
        Object readAtObj = event.get("readAt");
        String readAt = readAtObj != null ? readAtObj.toString() : null;

        if (messageId == null || conversationId == null) {
            log.warn("MESSAGE_READ event missing ids: {}", event);
            return;
        }

        String senderId = (String) event.get("senderId");
        if (senderId == null) {
            Message message = messageRepository.findById(messageId).orElse(null);
            if (message == null) {
                log.warn("Message not found for MESSAGE_READ event: {}", messageId);
                return;
            }
            senderId = message.getSenderId();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", messageId);
        payload.put("conversationId", conversationId);
        payload.put("status", "READ");
        payload.put("readAt", readAt);

        messagingTemplate.convertAndSendToUser(
                senderId,
                "/queue/message-read",
                payload
        );

        log.info("Sent MESSAGE_READ WS event to user {} for message {}", senderId, messageId);
    }}