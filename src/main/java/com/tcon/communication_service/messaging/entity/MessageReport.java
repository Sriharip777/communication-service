package com.tcon.communication_service.messaging.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Message Report Entity
 * Represents a user report of inappropriate message content
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_reports")
public class MessageReport {

    @Id
    private String id;

    @Indexed
    private String messageId;

    @Indexed
    private String reportedBy;

    private String reason;
    private String description;

    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;

    @CreatedDate
    private LocalDateTime createdAt;
}

