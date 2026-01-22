package com.tcon.communication_service.video.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Room Create Request DTO
 * Request to create a new video session room
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    @NotBlank(message = "Class session ID is required")
    private String classSessionId;

    @NotBlank(message = "Teacher ID is required")
    private String teacherId;

    @NotBlank(message = "Student ID is required")
    private String studentId;

    private String parentId;

    @NotNull(message = "Scheduled start time is required")
    @Future(message = "Scheduled start time must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledStartTime;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be positive")
    private Integer durationMinutes;

    @Builder.Default
    private Boolean recordingEnabled = true;

    private String subject;

    @Builder.Default
    private Boolean whiteboardEnabled = true;

    @Builder.Default
    private Boolean chatEnabled = true;
}
