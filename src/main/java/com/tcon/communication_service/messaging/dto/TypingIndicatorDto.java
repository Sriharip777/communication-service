package com.tcon.communication_service.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typing Indicator DTO
 * Indicates user typing status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorDto {

    private String conversationId;
    private String userId;
    private Boolean isTyping;
}
