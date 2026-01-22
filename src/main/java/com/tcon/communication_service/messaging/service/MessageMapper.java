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
                .isDeleted(entity.getIsDeleted())
                .isEdited(entity.getIsEdited())
                .editedAt(entity.getEditedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
