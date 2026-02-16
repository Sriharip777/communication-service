package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.messaging.dto.ConversationDto;
import com.tcon.communication_service.messaging.entity.Conversation;
import org.springframework.stereotype.Component;

/**
 * Conversation Mapper
 * Maps between Conversation entity and DTO
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Component
public class ConversationMapper {

    /**
     * Convert entity to DTO (without user-specific data)
     */
    public ConversationDto toDto(Conversation conversation) {
        if (conversation == null) {
            return null;
        }

        return ConversationDto.builder()
                .id(conversation.getId())
                .participantIds(conversation.getParticipantIds())
                .type(conversation.getType())
                .lastMessageId(conversation.getLastMessageId())
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageSenderId(conversation.getLastMessageSenderId())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCounts(conversation.getUnreadCounts())
                .unreadCount(0)  // Default to 0 if no userId provided
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    /**
     * ✅ Convert entity to DTO with user-specific unread count
     * @param conversation The conversation entity
     * @param userId Current user ID to calculate unread count for
     * @return ConversationDto with user's unread count
     */
    public ConversationDto toDto(Conversation conversation, String userId) {
        if (conversation == null) {
            return null;
        }

        // Get unread count for this specific user
        Integer userUnreadCount = conversation.getUnreadCount(userId);

        return ConversationDto.builder()
                .id(conversation.getId())
                .participantIds(conversation.getParticipantIds())
                .type(conversation.getType())
                .lastMessageId(conversation.getLastMessageId())
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageSenderId(conversation.getLastMessageSenderId())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCounts(conversation.getUnreadCounts())
                .unreadCount(userUnreadCount)  // ✅ Set user-specific unread count
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    /**
     * Convert DTO to entity
     */
    public Conversation toEntity(ConversationDto dto) {
        if (dto == null) {
            return null;
        }

        return Conversation.builder()
                .id(dto.getId())
                .participantIds(dto.getParticipantIds())
                .type(dto.getType())
                .lastMessageId(dto.getLastMessageId())
                .lastMessageContent(dto.getLastMessageContent())
                .lastMessageSenderId(dto.getLastMessageSenderId())
                .lastMessageAt(dto.getLastMessageAt())
                .unreadCounts(dto.getUnreadCounts())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}