package com.tcon.communication_service.video.dto;


import com.fasterxml.jackson.annotation.JsonFormat;

import com.tcon.communication_service.video.entity.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private String bookingId;
    private String teacherId;
    private String studentId;
    private String parentId;
    private String hundredMsRoomId;
    private String hundredMsRoomName;
    private String recordingId;
    private SessionStatus status;
    private Boolean canJoin;

    private Instant scheduledStartTime;
    private Instant scheduledEndTime;
    private Instant actualStartTime;
    private Instant endTime;
    private Instant createdAt;
    private Instant updatedAt;

    private Integer durationMinutes;
    private Integer actualDurationMinutes;
    private List<ParticipantDto> participants;
    private Boolean recordingEnabled;
    private String recordingUrl;
    private String recordingStatus;
    private SessionMetadataDto metadata;

}