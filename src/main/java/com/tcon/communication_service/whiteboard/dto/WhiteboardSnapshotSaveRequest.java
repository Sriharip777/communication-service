package com.tcon.communication_service.whiteboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhiteboardSnapshotSaveRequest {
    @NotBlank(message = "Class session ID is required")
    private String classSessionId;

    private String snapshotName;

    @NotBlank(message = "Snapshot data is required")
    private String snapshotData;
}