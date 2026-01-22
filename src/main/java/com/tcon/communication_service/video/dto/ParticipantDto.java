package com.tcon.communication_service.video.dto;


import com.fasterxml.jackson.annotation.JsonFormat;

import com.tcon.communication_service.video.entity.ParticipantRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Participant DTO
 * Data transfer object for session participant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDto {

    private String userId;
    private ParticipantRole role;
    private String hundredMsPeerId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime joinedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime leftAt;

    private Boolean isCameraEnabled;
    private Boolean isMicEnabled;
    private Boolean isScreenSharing;
    private Integer durationMinutes;
}

