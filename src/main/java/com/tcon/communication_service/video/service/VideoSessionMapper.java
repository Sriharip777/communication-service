package com.tcon.communication_service.video.service;

import com.tcon.communication_service.video.dto.ParticipantDto;
import com.tcon.communication_service.video.dto.RoomConfigDto;
import com.tcon.communication_service.video.dto.SessionMetadataDto;
import com.tcon.communication_service.video.dto.VideoSessionDto;
import com.tcon.communication_service.video.entity.SessionMetadata;
import com.tcon.communication_service.video.entity.SessionParticipant;
import com.tcon.communication_service.video.entity.VideoSession;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Video Session Mapper
 * Maps between entities and DTOs
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Component
public class VideoSessionMapper {

    public VideoSessionDto toDto(VideoSession entity) {
        if (entity == null) {
            return null;
        }

        return VideoSessionDto.builder()
                .id(entity.getId())
                .classSessionId(entity.getClassSessionId())
                .teacherId(entity.getTeacherId())
                .studentId(entity.getStudentId())
                .parentId(entity.getParentId())
                .hundredMsRoomId(entity.getHundredMsRoomId())
                .hundredMsRoomName(entity.getHundredMsRoomName())
                .recordingId(entity.getRecordingId())
                .status(entity.getStatus())
                .scheduledStartTime(entity.getScheduledStartTime())
                .actualStartTime(entity.getActualStartTime())
                .endTime(entity.getEndTime())
                .durationMinutes(entity.getDurationMinutes())
                .actualDurationMinutes(entity.getActualDurationMinutes())
                .participants(toParticipantDtoList(entity.getParticipants()))
                .recordingEnabled(entity.getRecordingEnabled())
                .recordingUrl(entity.getRecordingUrl())
                .recordingStatus(entity.getRecordingStatus())
                .metadata(toMetadataDto(entity.getMetadata()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ParticipantDto toParticipantDto(SessionParticipant entity) {
        if (entity == null) {
            return null;
        }

        return ParticipantDto.builder()
                .userId(entity.getUserId())
                .role(entity.getRole())
                .hundredMsPeerId(entity.getHundredMsPeerId())
                .joinedAt(entity.getJoinedAt())
                .leftAt(entity.getLeftAt())
                .isCameraEnabled(entity.getIsCameraEnabled())
                .isMicEnabled(entity.getIsMicEnabled())
                .isScreenSharing(entity.getIsScreenSharing())
                .durationMinutes(entity.getDurationMinutes())
                .build();
    }

    public List<ParticipantDto> toParticipantDtoList(List<SessionParticipant> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList());
    }

    public SessionMetadataDto toMetadataDto(SessionMetadata entity) {
        if (entity == null) {
            return null;
        }

        return SessionMetadataDto.builder()
                .whiteboardEnabled(entity.getWhiteboardEnabled())
                .chatEnabled(entity.getChatEnabled())
                .screenShareEnabled(entity.getScreenShareEnabled())
                .handRaiseEnabled(entity.getHandRaiseEnabled())
                .roomQuality(entity.getRoomQuality())
                .maxParticipants(entity.getMaxParticipants())
                .subject(entity.getSubject())
                .notes(entity.getNotes())
                .build();
    }

    public RoomConfigDto toRoomConfigDto(SessionMetadata entity) {
        if (entity == null) {
            return null;
        }

        return RoomConfigDto.builder()
                .recordingEnabled(true)
                .whiteboardEnabled(entity.getWhiteboardEnabled())
                .chatEnabled(entity.getChatEnabled())
                .screenShareEnabled(entity.getScreenShareEnabled())
                .quality(entity.getRoomQuality())
                .maxParticipants(entity.getMaxParticipants())
                .build();
    }
}
