package com.tcon.communication_service.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tcon.communication_service.messaging.entity.MessageStatus;
import com.tcon.communication_service.messaging.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message DTO
 * Data transfer object for message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private String id;
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String content;
    private MessageType type;
    private MessageStatus status;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String replyToMessageId;
    private String replyToContent;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime readAt;

    private Boolean isDeleted;
    private Boolean isEdited;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime editedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
