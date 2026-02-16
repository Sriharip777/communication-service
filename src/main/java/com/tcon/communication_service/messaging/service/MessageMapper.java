package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.messaging.dto.MessageDto;
import com.tcon.communication_service.messaging.entity.Message;
import org.springframework.stereotype.Component;

/**
 * Message Mapper
 * Maps between message entities and DTOs
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Component
public class MessageMapper {

    /**
     * Convert Message entity to DTO
     * ✅ UPDATED: Added deliveredAt field
     *
     * @param entity Message entity
     * @return MessageDto
     */
    public MessageDto toDto(Message entity) {
        if (entity == null) {
            return null;
        }

        return MessageDto.builder()
                .id(entity.getId())
                .conversationId(entity.getConversationId())
                .senderId(entity.getSenderId())
                .receiverId(entity.getReceiverId())
                .content(entity.getContent())
                .type(entity.getType())
                .status(entity.getStatus())
                .fileUrl(entity.getFileUrl())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .mimeType(entity.getMimeType())
                .replyToMessageId(entity.getReplyToMessageId())
                .replyToContent(entity.getReplyToContent())
                .readAt(entity.getReadAt())
                .deliveredAt(entity.getDeliveredAt())  // ✅ ADDED
                .isDeleted(entity.getIsDeleted())
                .isEdited(entity.getIsEdited())
                .editedAt(entity.getEditedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Convert MessageDto to entity
     * ✅ UPDATED: Added deliveredAt field
     *
     * @param dto MessageDto
     * @return Message entity
     */
    public Message toEntity(MessageDto dto) {
        if (dto == null) {
            return null;
        }

        return Message.builder()
                .id(dto.getId())
                .conversationId(dto.getConversationId())
                .senderId(dto.getSenderId())
                .receiverId(dto.getReceiverId())
                .content(dto.getContent())
                .type(dto.getType())
                .status(dto.getStatus())
                .fileUrl(dto.getFileUrl())
                .fileName(dto.getFileName())
                .fileSize(dto.getFileSize())
                .mimeType(dto.getMimeType())
                .replyToMessageId(dto.getReplyToMessageId())
                .replyToContent(dto.getReplyToContent())
                .readAt(dto.getReadAt())
                .deliveredAt(dto.getDeliveredAt())  // ✅ ADDED
                .isDeleted(dto.getIsDeleted())
                .isEdited(dto.getIsEdited())
                .editedAt(dto.getEditedAt())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}