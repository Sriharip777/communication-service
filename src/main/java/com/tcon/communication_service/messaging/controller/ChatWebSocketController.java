package com.tcon.communication_service.messaging.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.dto.MessageSendRequest;
import com.tcon.communication_service.messaging.dto.TypingIndicatorDto;
import com.tcon.communication_service.messaging.exception.ParentAccessDeniedException;
import com.tcon.communication_service.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * ✅ FIXED: senderRole now passed to service (3-param method)
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload String rawPayload,
                            @Header("X-User-Id") String senderId,
                            @Header("X-User-Role") String senderRole,
                            SimpMessageHeaderAccessor headerAccessor) {


        try {
            MessageSendRequest request = objectMapper.readValue(rawPayload, MessageSendRequest.class);

            if (request.getReceiverId() == null || request.getContent() == null ||
                    request.getContent().trim().isEmpty()) {
                throw new IllegalArgumentException("Missing receiverId or content");
            }

            log.info("📤 WS Message {}→{}: {}", senderId, request.getReceiverId(),
                    request.getContent().substring(0, Math.min(50, request.getContent().length())));

            // ✅ FIXED: Pass senderRole as second param to match service signature
            MessageDto message = messageService.sendMessage(senderId, senderRole, request);

            String convId = message.getConversationId();
            messagingTemplate.convertAndSend("/topic/conversations/" + convId, message);
            messagingTemplate.convertAndSendToUser(message.getReceiverId(), "/queue/messages", message);
            messagingTemplate.convertAndSendToUser(senderId, "/queue/delivery", message);

            log.debug("✅ WS Sent conv={} msg={}", convId, message.getId());

        } catch (ParentAccessDeniedException e) {
            log.warn("🚫 Parent blocked: {}", e.getMessage());
            messagingTemplate.convertAndSendToUser(senderId, "/queue/errors", e.getMessage());
        } catch (Exception e) {
            log.error("❌ WS Send failed {}: {}", senderId, e.getMessage(), e);
            messagingTemplate.convertAndSendToUser(senderId, "/queue/errors",
                    "Send failed: " + e.getMessage());
        }
    }

    @MessageMapping("/chat.typing/{conversationId}")
    public void sendTypingIndicator(@DestinationVariable String conversationId,
                                    @Payload TypingIndicatorDto indicator) {
        indicator.setConversationId(conversationId);
        messagingTemplate.convertAndSend("/topic/typing/" + conversationId, indicator);
    }

    @MessageMapping("/chat.join/{conversationId}")
    public void joinConversation(@DestinationVariable String conversationId,
                                 @Payload String userId) {
        messagingTemplate.convertAndSend("/topic/join/" + conversationId, userId);
    }

    @MessageMapping("/chat.leave/{conversationId}")
    public void leaveConversation(@DestinationVariable String conversationId,
                                  @Payload String userId) {
        messagingTemplate.convertAndSend("/topic/leave/" + conversationId, userId);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable ex) {
        log.error("❌ WebSocket error: {}", ex.getMessage());
        return "Error: " + ex.getMessage();
    }
}