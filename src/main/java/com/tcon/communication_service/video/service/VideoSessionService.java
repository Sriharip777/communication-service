package com.tcon.communication_service.video.service;

import com.tcon.communication_service.video.dto.AgoraTokenResponse;
import com.tcon.communication_service.video.dto.RoomCreateRequest;
import com.tcon.communication_service.video.dto.RoomJoinResponse;
import com.tcon.communication_service.video.dto.VideoSessionDto;
import com.tcon.communication_service.video.entity.ParticipantRole;
import com.tcon.communication_service.video.entity.SessionMetadata;
import com.tcon.communication_service.video.entity.SessionParticipant;
import com.tcon.communication_service.video.entity.SessionStatus;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.integration.AgoraClient;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSessionService {

    private final VideoSessionRepository videoSessionRepository;
    private final AgoraClient agoraClient;
    private final VideoSessionMapper videoSessionMapper;

    // ==================== CREATE ROOM ====================

    @Transactional
    public VideoSessionDto createRoom(RoomCreateRequest request) {
        log.info("Creating video session room for class: {}", request.getClassSessionId());

        if (videoSessionRepository.existsByClassSessionId(request.getClassSessionId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Video session already exists for this class"
            );
        }

        String roomName = generateRoomName(request);
        String channelName = agoraClient.createRoom(roomName, request.getDurationMinutes());

        String sanitizedParentId = (request.getParentId() != null && !request.getParentId().isBlank())
                ? request.getParentId()
                : null;

        VideoSession session = VideoSession.builder()
                .classSessionId(request.getClassSessionId())
                .teacherId(request.getTeacherId())
                .studentId(request.getStudentId())
                .parentId(sanitizedParentId)
                .hundredMsRoomId(channelName)
                .hundredMsRoomName(roomName)
                .status(SessionStatus.SCHEDULED)
                .scheduledStartTime(request.getScheduledStartTime())
                .durationMinutes(request.getDurationMinutes())
                .recordingEnabled(request.getRecordingEnabled())
                .metadata(SessionMetadata.builder()
                        .whiteboardEnabled(request.getWhiteboardEnabled())
                        .chatEnabled(request.getChatEnabled())
                        .screenShareEnabled(true)
                        .handRaiseEnabled(true)
                        .roomQuality("720p")
                        .subject(request.getSubject())
                        .build())
                .build();

        VideoSession saved = videoSessionRepository.save(session);
        log.info("✅ Video session created: id={}, teacher={}, student={}, parent={}",
                saved.getId(), saved.getTeacherId(), saved.getStudentId(),
                saved.getParentId() != null ? saved.getParentId() : "none");

        return videoSessionMapper.toDto(saved);
    }

    // ==================== JOIN SESSION ====================

    @Transactional
    public RoomJoinResponse joinSession(String sessionId, String userId, ParticipantRole role) {
        log.info("User {} joining session {} as {}", userId, sessionId, role);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));

        if (!session.canJoin()) {
            LocalDateTime joinWindowStart = session.getScheduledStartTime().minusMinutes(15);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format(
                            "Session cannot be joined yet. Join window opens at %s (15 minutes before class starts at %s)",
                            joinWindowStart, session.getScheduledStartTime()
                    )
            );
        }

        validateUserAccess(session, userId, role);

        if (session.getParticipants() == null) {
            session.setParticipants(new ArrayList<>());
        }

        boolean alreadyJoined = session.getParticipants().stream()
                .anyMatch(p -> userId.equals(p.getUserId()) && p.getLeftAt() == null);

        if (alreadyJoined) {
            log.warn("⚠️ User {} already joined session {}, returning fresh token", userId, sessionId);

            AgoraTokenResponse tokenResponse = agoraClient.generateAuthToken(
                    session.getHundredMsRoomId(),
                    userId,
                    role.name()
            );

            return RoomJoinResponse.builder()
                    .sessionId(session.getId())
                    .roomId(session.getHundredMsRoomId())
                    .roomName(session.getHundredMsRoomName())
                    .authToken(tokenResponse.getToken())
                    .agoraUid(tokenResponse.getUid())
                    .peerId(userId)
                    .role(role.name())
                    .roomConfig(videoSessionMapper.toRoomConfigDto(session.getMetadata()))
                    .webrtcUrl("wss://agora-rtc.io")
                    .expiresAt(tokenResponse.getExpiresAt())
                    .build();
        }

        AgoraTokenResponse tokenResponse = agoraClient.generateAuthToken(
                session.getHundredMsRoomId(),
                userId,
                role.name()
        );

        log.info("✅ Agora token generated - UID: {}", tokenResponse.getUid());

        SessionParticipant participant = SessionParticipant.builder()
                .userId(userId)
                .role(role)
                .hundredMsPeerId(userId)
                .joinedAt(LocalDateTime.now())
                .isCameraEnabled(role != ParticipantRole.PARENT_OBSERVER)
                .isMicEnabled(role != ParticipantRole.PARENT_OBSERVER)
                .isScreenSharing(false)
                .build();

        session.addParticipant(participant);

        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.startSession();
        }

        videoSessionRepository.save(session);

        log.info("✅ User {} joined session {} as {}", userId, sessionId, role);

        return RoomJoinResponse.builder()
                .sessionId(session.getId())
                .roomId(session.getHundredMsRoomId())
                .roomName(session.getHundredMsRoomName())
                .authToken(tokenResponse.getToken())
                .agoraUid(tokenResponse.getUid())
                .peerId(userId)
                .role(role.name())
                .roomConfig(videoSessionMapper.toRoomConfigDto(session.getMetadata()))
                .webrtcUrl("wss://agora-rtc.io")
                .expiresAt(tokenResponse.getExpiresAt())
                .build();
    }

    // ==================== END SESSION ====================

    @Transactional
    public VideoSessionDto endSession(String sessionId, String userId) {
        log.info("Ending session {} by user {}", sessionId, userId);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));

        if (!session.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session is not active"
            );
        }

        if (!userId.equals(session.getTeacherId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only teacher can end the session"
            );
        }

        if (session.getParticipants() != null) {
            session.getParticipants().stream()
                    .filter(SessionParticipant::isActive)
                    .forEach(SessionParticipant::leave);
        }

        session.endSession();

        if (Boolean.TRUE.equals(session.getRecordingEnabled()) && session.getRecordingId() != null) {
            agoraClient.stopRecording(session.getRecordingId());
        }

        VideoSession saved = videoSessionRepository.save(session);
        log.info("Session ended successfully: {}", saved.getId());

        return videoSessionMapper.toDto(saved);
    }

    // ==================== GETTERS ====================

    public VideoSessionDto getSessionById(String sessionId) {
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));
        return videoSessionMapper.toDto(session);
    }

    public VideoSessionDto getSessionByClassId(String classSessionId) {
        log.info("🔍 Finding video session for class: {}", classSessionId);

        VideoSession session = videoSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> {
                    log.error("❌ Session not found for class: {}", classSessionId);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Video session not found for class: " + classSessionId
                    );
                });

        log.info("✅ Session found: id={}, status={}, roomId={}",
                session.getId(), session.getStatus(), session.getHundredMsRoomId());

        try {
            log.info("🔄 Converting to DTO...");
            VideoSessionDto dto = videoSessionMapper.toDto(session);
            log.info("✅ DTO created successfully: {}", dto.getId());
            return dto;
        } catch (Exception e) {
            log.error("❌ MAPPER ERROR: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to convert session to DTO"
            );
        }
    }

    public List<VideoSessionDto> getTeacherSessions(String teacherId) {
        log.info("📋 Fetching ALL sessions for teacher: {}", teacherId);

        List<VideoSession> sessions = videoSessionRepository
                .findByTeacherIdOrderByScheduledStartTimeDesc(teacherId);

        log.info("✅ Found {} total sessions for teacher {}", sessions.size(), teacherId);

        return sessions.stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

    public List<VideoSessionDto> getStudentSessions(String studentId) {
        log.info("📋 Fetching ALL sessions for student: {}", studentId);

        List<VideoSession> sessions = videoSessionRepository
                .findByStudentIdOrderByScheduledStartTimeDesc(studentId);

        log.info("✅ Found {} total sessions for student {}", sessions.size(), studentId);

        return sessions.stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

    public List<VideoSessionDto> getActiveSessions() {
        return videoSessionRepository.findByStatusAndScheduledStartTimeBefore(
                        SessionStatus.IN_PROGRESS,
                        LocalDateTime.now())
                .stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

    // ==================== RECORDING ====================

    @Transactional
    public void startRecording(String sessionId) {
        log.info("Starting recording for session: {}", sessionId);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));

        if (!session.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session must be active to start recording"
            );
        }

        String recordingId = agoraClient.startRecording(session.getHundredMsRoomId());
        session.setRecordingId(recordingId);
        session.setRecordingStatus("RECORDING");

        videoSessionRepository.save(session);
        log.info("Recording started with ID: {}", recordingId);
    }

    // ==================== UTILITY ====================

    public boolean existsByClassSessionId(String classSessionId) {
        return videoSessionRepository.existsByClassSessionId(classSessionId);
    }

    private String generateRoomName(RoomCreateRequest request) {
        return String.format("class_%s_%s",
                request.getClassSessionId(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    // ==================== ACCESS VALIDATION ====================

    private void validateUserAccess(VideoSession session, String userId, ParticipantRole role) {
        boolean hasAccess = switch (role) {
            case TEACHER -> userId.equals(session.getTeacherId());
            case STUDENT -> userId.equals(session.getStudentId());
            case PARENT_OBSERVER -> {
                if (session.getParentId() != null
                        && !session.getParentId().isBlank()
                        && userId.equals(session.getParentId())) {
                    log.info("✅ Parent {} authorized via parentId match", userId);
                    yield true;
                }

                if (session.getParentId() == null || session.getParentId().isBlank()) {
                    log.info("✅ Parent {} authorized (no specific parentId set on session)", userId);
                    yield true;
                }

                log.warn("❌ Parent {} not authorized. Session parentId: {}", userId, session.getParentId());
                yield false;
            }
        };

        if (!hasAccess) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User not authorized to join this session"
            );
        }
    }
}
