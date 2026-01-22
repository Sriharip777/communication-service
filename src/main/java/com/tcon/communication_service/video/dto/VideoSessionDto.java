package com.tcon.communication_service.video.dto;


import com.fasterxml.jackson.annotation.JsonFormat;

import com.tcon.communication_service.video.entity.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Video Session DTO
 * Data transfer object for video session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoSessionDto {

    private String id;
    private String classSessionId;
    private String teacherId;
    private String studentId;
    private String parentId;
    private String hundredMsRoomId;
    private String hundredMsRoomName;
    private String recordingId;
    private SessionStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime actualStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    private Integer durationMinutes;
    private Integer actualDurationMinutes;
    private List<ParticipantDto> participants;
    private Boolean recordingEnabled;
    private String recordingUrl;
    private String recordingStatus;
    private SessionMetadataDto metadata;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
