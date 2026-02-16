package com.tcon.communication_service.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Conversation DTO
 * Data transfer object for conversations
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {

    private String id;
    private List<String> participantIds;
    private String type;  // DIRECT or GROUP

    private String lastMessageId;
    private String lastMessageContent;
    private String lastMessageSenderId;
    private LocalDateTime lastMessageAt;
    private Map<String, Integer> unreadCounts;
    private Integer unreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
