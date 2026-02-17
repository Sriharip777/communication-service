package com.tcon.communication_service.whiteboard.dto;

import com.tcon.communication_service.video.entity.SessionType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhiteboardOpenRequest {
    @NotBlank(message = "Class session ID is required")
    private String classSessionId;

    private String courseId;

    @NotBlank(message = "Teacher ID is required")
    private String teacherId;

    private String teacherName;

    private SessionType sessionType;

    private String studentId;  // For SOLO/DEMO

    private List<String> enrolledStudentIds;  // For GROUP
}