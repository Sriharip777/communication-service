package com.tcon.communication_service.messaging.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unread Count
 * Tracks unread message count per user in a conversation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCount {
    private String userId;

    @Builder.Default
    private Integer count = 0;
}
