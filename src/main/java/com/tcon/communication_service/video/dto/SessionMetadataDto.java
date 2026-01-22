package com.tcon.communication_service.video.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session Metadata DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMetadataDto {

    private Boolean whiteboardEnabled;
    private Boolean chatEnabled;
    private Boolean screenShareEnabled;
    private Boolean handRaiseEnabled;
    private String roomQuality;
    private Integer maxParticipants;
    private String subject;
    private String notes;
}
