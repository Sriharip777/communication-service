package com.tcon.communication_service.messaging.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Typing Indicator
 * Tracks when a user is typing in a conversation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicator {
    private String userId;
    private LocalDateTime startedAt;
}
