package com.tcon.communication_service.whiteboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tcon.communication_service.video.entity.SessionType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Whiteboard Room Entity
 * Links to existing VideoSession via classSessionId
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "whiteboard_rooms")
@CompoundIndexes({
        @CompoundIndex(name = "class_session_idx", def = "{'classSessionId': 1}"),
        @CompoundIndex(name = "teacher_status_idx", def = "{'teacherId': 1, 'isActive': 1}"),
        @CompoundIndex(name = "course_idx", def = "{'courseId': 1}")
})
public class WhiteboardRoom {

    @Id
    private String id;

    /**
     * Links to VideoSession.classSessionId
     */
    @Indexed(unique = true)
    private String classSessionId;

    /**
     * For GROUP sessions - links to Course
     */
    @Indexed
    private String courseId;

    /**
     * Agora Whiteboard Room UUID
     */
    private String agoraWhiteboardUuid;

    private String teamUUID;
    private String appUUID;

    @Indexed
    private String teacherId;
    private String teacherName;

    /**
     * Session type from VideoSession
     */
    private SessionType sessionType;

    /**
     * For GROUP classes: enrolled student IDs
     */
    @Builder.Default
    private List<String> enrolledStudentIds = new ArrayList<>();

    /**
     * Students currently viewing whiteboard
     */
    @Builder.Default
    private List<String> activeStudentIds = new ArrayList<>();

    /**
     * Student ID for SOLO/DEMO sessions
     */
    private String studentId;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isBan = false;

    /**
     * Number of snapshots saved
     */
    @Builder.Default
    private Integer snapshotCount = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime closedAt;

    private LocalDateTime lastDisconnectTime;

    @LastModifiedDate
    private LocalDateTime updatedAt;


}