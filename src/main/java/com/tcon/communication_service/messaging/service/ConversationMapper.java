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
     * Convert entity to DTO
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
