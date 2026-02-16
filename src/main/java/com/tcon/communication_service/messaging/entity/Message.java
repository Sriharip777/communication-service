package com.tcon.communication_service.messaging.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Message Entity
 * Represents a chat message between users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "conversation_timestamp_idx", def = "{'conversationId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "sender_receiver_idx", def = "{'senderId': 1, 'receiverId': 1}")
})
public class Message {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    @Indexed
    private String senderId;

    @Indexed
    private String receiverId;

    private LocalDateTime  deliveredAt;

    private String content;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    // For file/image messages
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;

    // Reply functionality
    private String replyToMessageId;
    private String replyToContent; // Cached for performance

    // Read receipts
    private LocalDateTime readAt;

    // Message retention - auto-delete after 30 days
    // TTL index created programmatically in MongoIndexConfig
    @Indexed  // ← Removed expireAfterSeconds
    private LocalDateTime expiresAt;

    // Moderation
    @Builder.Default
    private Boolean isDeleted = false;

    @Builder.Default
    private Boolean isEdited = false;

    private LocalDateTime editedAt;

    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    // Helper methods
    public void markAsRead() {
        this.status = MessageStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    public void markAsDelivered() {
        this.status = MessageStatus.DELIVERED;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.content = "[Message deleted]";
        this.fileUrl = null;
    }

    public void edit(String newContent) {
        this.content = newContent;
        this.isEdited = true;
        this.editedAt = LocalDateTime.now();
    }

    public boolean canBeEdited() {
        return !isDeleted && type == MessageType.TEXT
                && createdAt.plusMinutes(15).isAfter(LocalDateTime.now());
    }

    public boolean canBeDeleted() {
        return !isDeleted;
    }
}
