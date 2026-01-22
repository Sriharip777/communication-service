package com.tcon.communication_service.messaging.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation Entity
 * Represents a conversation between users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    @Indexed
    private List<String> participantIds;

    @Builder.Default
    private String type = "DIRECT";  // ← Added type field (DIRECT or GROUP)

    private String lastMessageId;
    private String lastMessageContent;
    private String lastMessageSenderId;
    private LocalDateTime lastMessageAt;

    @Builder.Default
    private Map<String, Integer> unreadCounts = new HashMap<>();  // userId -> count

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Helper methods
    public void updateLastMessage(String messageId, String content, String senderId) {
        this.lastMessageId = messageId;
        this.lastMessageContent = content;
        this.lastMessageSenderId = senderId;
        this.lastMessageAt = LocalDateTime.now();

        // Increment unread count for other participants
        participantIds.stream()
                .filter(id -> !id.equals(senderId))
                .forEach(id -> unreadCounts.merge(id, 1, Integer::sum));
    }

    public void resetUnreadCount(String userId) {
        unreadCounts.put(userId, 0);
    }

    public int getUnreadCount(String userId) {
        return unreadCounts.getOrDefault(userId, 0);
    }
}
