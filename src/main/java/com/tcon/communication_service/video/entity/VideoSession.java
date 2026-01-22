package com.tcon.communication_service.video.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Video Session Entity
 * Represents a video conferencing session using 100ms
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "video_sessions")
@CompoundIndexes({
        @CompoundIndex(name = "class_session_idx", def = "{'classSessionId': 1}"),
        @CompoundIndex(name = "teacher_status_idx", def = "{'teacherId': 1, 'status': 1}"),
        @CompoundIndex(name = "student_status_idx", def = "{'studentId': 1, 'status': 1}"),
        @CompoundIndex(name = "start_time_idx", def = "{'startTime': -1}")
})
public class VideoSession {

    @Id
    private String id;

    @Indexed
    private String classSessionId;

    @Indexed
    private String teacherId;

    @Indexed
    private String studentId;

    private String parentId; // Optional parent observer

    // 100ms specific fields
    private String hundredMsRoomId;
    private String hundredMsRoomName;
    private String recordingId;

    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    private LocalDateTime scheduledStartTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime endTime;

    private Integer durationMinutes;
    private Integer actualDurationMinutes;

    @Builder.Default
    private List<SessionParticipant> participants = new ArrayList<>();

    // Recording details
    @Builder.Default
    private Boolean recordingEnabled = true;
    private String recordingUrl;
    private String recordingStatus;

    // Session metadata
    @Builder.Default
    private SessionMetadata metadata = new SessionMetadata();

    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Helper methods
    public void addParticipant(SessionParticipant participant) {
        if (this.participants == null) {
            this.participants = new ArrayList<>();
        }
        this.participants.add(participant);
    }

    public void startSession() {
        this.status = SessionStatus.IN_PROGRESS;
        this.actualStartTime = LocalDateTime.now();
    }

    public void endSession() {
        this.status = SessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        if (this.actualStartTime != null) {
            this.actualDurationMinutes = (int) java.time.Duration
                    .between(this.actualStartTime, this.endTime)
                    .toMinutes();
        }
    }

    public void cancelSession() {
        this.status = SessionStatus.CANCELLED;
    }

    public boolean isActive() {
        return status == SessionStatus.IN_PROGRESS;
    }

    public boolean canJoin() {
        return status == SessionStatus.SCHEDULED || status == SessionStatus.IN_PROGRESS;
    }
}
