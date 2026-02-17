package com.tcon.communication_service.whiteboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Whiteboard Snapshot Entity
 * Saves whiteboard state at specific moments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "whiteboard_snapshots")
@CompoundIndexes({
        @CompoundIndex(name = "class_session_snapshot_idx",
                def = "{'classSessionId': 1, 'snapshotNumber': 1}"),
        @CompoundIndex(name = "course_idx", def = "{'courseId': 1}")
})
public class WhiteboardSnapshot {

    @Id
    private String id;

    /**
     * Links to VideoSession.classSessionId
     */
    @Indexed
    private String classSessionId;

    /**
     * For GROUP classes
     */
    @Indexed
    private String courseId;

    /**
     * Agora whiteboard room UUID
     */
    private String agoraWhiteboardUuid;

    private String snapshotName;

    /**
     * JSON data of whiteboard state
     */
    private String snapshotData;

    /**
     * Optional: Preview image URL (for future)
     */
    private String imageUrl;

    private String teacherId;
    private String teacherName;

    /**
     * Students authorized to view this snapshot
     * - For SOLO/DEMO: single student
     * - For GROUP: all enrolled students
     */
    @Builder.Default
    private List<String> authorizedStudentIds = new ArrayList<>();

    /**
     * Snapshot number in the session (Page 1, Page 2, etc.)
     */
    private Integer snapshotNumber;

    @CreatedDate
    private LocalDateTime createdAt;
}
