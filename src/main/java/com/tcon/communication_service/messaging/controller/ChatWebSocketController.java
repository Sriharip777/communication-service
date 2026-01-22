package com.tcon.communication_service.messaging.controller;

import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.dto.MessageSendRequest;
import com.tcon.communication_service.messaging.dto.TypingIndicatorDto;
import com.tcon.communication_service.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket Chat Controller
 * Handles real-time messaging via WebSocket/STOMP
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle incoming chat messages via WebSocket
     * Client sends to: /app/chat.sendMessage
     * Headers must include: X-User-Id (sender's ID)
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload MessageSendRequest request,
                            @Header("X-User-Id") String senderId,
                            SimpMessageHeaderAccessor headerAccessor) {
        log.info("WebSocket message received from {} to {}", senderId, request.getReceiverId());

        try {
            // Save message - pass senderId explicitly
            MessageDto message = messageService.sendMessage(senderId, request);

            // Send to conversation topic (both users subscribed)
            String conversationDestination = "/topic/conversations/" + message.getConversationId();
            messagingTemplate.convertAndSend(conversationDestination, message);

            // Send to specific receiver's queue
            messagingTemplate.convertAndSendToUser(
                    message.getReceiverId(),  // ← Fixed: use receiverId, not recipientId
                    "/queue/messages",
                    message
            );

            log.debug("Message delivered: {}", message.getId());

        } catch (Exception e) {
            log.error("Error sending WebSocket message: {}", e.getMessage(), e);

            // Send error back to sender
            messagingTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/errors",
                    "Failed to send message: " + e.getMessage()
            );
        }
    }

    /**
     * Handle typing indicator
     */
    @MessageMapping("/chat.typing/{conversationId}")
    public void sendTypingIndicator(
            @DestinationVariable String conversationId,
            @Payload TypingIndicatorDto indicator) {
        log.debug("Typing indicator: {} in conversation {}, isTyping: {}",
                indicator.getUserId(), conversationId, indicator.getIsTyping());

        // Broadcast typing indicator to conversation
        messagingTemplate.convertAndSend(
                "/topic/typing/" + conversationId,
                indicator
        );
    }

    /**
     * Handle user joining conversation
     */
    @MessageMapping("/chat.join/{conversationId}")
    public void joinConversation(
            @DestinationVariable String conversationId,
            @Payload String userId) {
        log.info("User {} joined conversation {}", userId, conversationId);

        // Notify other participants
        messagingTemplate.convertAndSend(
                "/topic/join/" + conversationId,
                userId
        );
    }

    /**
     * Handle user leaving conversation
     */
    @MessageMapping("/chat.leave/{conversationId}")
    public void leaveConversation(
            @DestinationVariable String conversationId,
            @Payload String userId) {
        log.info("User {} left conversation {}", userId, conversationId);

        messagingTemplate.convertAndSend(
                "/topic/leave/" + conversationId,
                userId
        );
    }
}
