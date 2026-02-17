package com.tcon.communication_service.whiteboard.dto;

import com.tcon.communication_service.video.entity.ParticipantRole;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhiteboardAccessRequest {
    @NotBlank(message = "Class session ID is required")
    private String classSessionId;

    @NotBlank(message = "User ID is required")
    private String userId;

    private ParticipantRole role;
}