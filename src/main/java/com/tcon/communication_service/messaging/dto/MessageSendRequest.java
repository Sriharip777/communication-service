package com.tcon.communication_service.messaging.dto;

import com.tcon.communication_service.messaging.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message Send Request DTO
 * Request to send a new message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotBlank(message = "Content is required")
    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String replyToMessageId;
}
