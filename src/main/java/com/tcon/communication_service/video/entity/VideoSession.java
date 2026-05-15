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

import java.time.Duration;
import java.time.Instant;
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
        @CompoundIndex(name = "booking_idx", def = "{'bookingId': 1}"),
        @CompoundIndex(name = "teacher_status_idx", def = "{'teacherId': 1, 'status': 1}"),
        @CompoundIndex(name = "student_status_idx", def = "{'studentId': 1, 'status': 1}"),
        @CompoundIndex(name = "scheduled_start_time_idx", def = "{'scheduledStartTime': -1}")
})
public class VideoSession {

    @Id
    private String id;

    @Indexed
    private String classSessionId;

    @Indexed
    private String bookingId;

    @Indexed
    private String teacherId;

    @Indexed
    private String studentId;

    private String parentId;

    private String hundredMsRoomId;
    private String hundredMsRoomName;
    private String recordingId;

    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Indexed
    private Instant scheduledStartTime;

    private Instant scheduledEndTime;
    private Instant actualStartTime;
    private Instant endTime;

    private Integer durationMinutes;
    private Integer actualDurationMinutes;

    @Builder.Default
    private List<SessionParticipant> participants = new ArrayList<>();

    @Builder.Default
    private Boolean recordingEnabled = true;
    private String recordingUrl;
    private String recordingStatus;

    @Builder.Default
    private SessionMetadata metadata = new SessionMetadata();

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void addParticipant(SessionParticipant participant) {
        if (this.participants == null) {
            this.participants = new ArrayList<>();
        }
        this.participants.add(participant);
    }

    public void startSession() {
        this.status = SessionStatus.IN_PROGRESS;
        this.actualStartTime = Instant.now();
    }

    public void endSession() {
        this.status = SessionStatus.COMPLETED;
        this.endTime = Instant.now();
        if (this.actualStartTime != null) {
            this.actualDurationMinutes = (int) Duration
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
        if (scheduledStartTime == null) {
            return status == SessionStatus.SCHEDULED
                    || status == SessionStatus.IN_PROGRESS;
        }

        Instant now = Instant.now();
        Instant joinFrom = scheduledStartTime.minusSeconds(15 * 60);

        Instant joinUntil;
        if (scheduledEndTime != null) {
            joinUntil = scheduledEndTime;
        } else if (durationMinutes != null) {
            joinUntil = scheduledStartTime.plusSeconds((long) durationMinutes * 60);
        } else {
            joinUntil = scheduledStartTime.plusSeconds(2 * 60 * 60);
        }

        boolean timeOk = !now.isBefore(joinFrom) && now.isBefore(joinUntil);
        boolean statusOk = status == SessionStatus.SCHEDULED
                || status == SessionStatus.IN_PROGRESS;

        return timeOk && statusOk;
    }
}