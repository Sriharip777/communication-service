package com.tcon.communication_service.video.service;

import com.tcon.communication_service.video.dto.AgoraTokenResponse;
import com.tcon.communication_service.video.integration.AgoraClient;  // Changed import
import com.tcon.communication_service.video.dto.RoomCreateRequest;
import com.tcon.communication_service.video.dto.RoomJoinResponse;
import com.tcon.communication_service.video.dto.VideoSessionDto;
import com.tcon.communication_service.video.entity.*;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Video Session Service
 * Business logic for video session management
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSessionService {

    private final VideoSessionRepository videoSessionRepository;
    private final AgoraClient agoraClient;  // Changed from HundredMsClient
    private final VideoSessionMapper videoSessionMapper;

    @Transactional
    public VideoSessionDto createRoom(RoomCreateRequest request) {
        log.info("Creating video session room for class: {}", request.getClassSessionId());

        // Check if session already exists
        if (videoSessionRepository.existsByClassSessionId(request.getClassSessionId())) {
            throw new IllegalStateException("Video session already exists for this class");
        }

        // Create channel in Agora
        String roomName = generateRoomName(request);
        String channelName = agoraClient.createRoom(roomName, request.getDurationMinutes());

        // Build video session entity
        VideoSession session = VideoSession.builder()
                .classSessionId(request.getClassSessionId())
                .teacherId(request.getTeacherId())
                .studentId(request.getStudentId())
                .parentId(request.getParentId())
                .hundredMsRoomId(channelName)  // Store Agora channel name
                .hundredMsRoomName(roomName)   // Store original room name
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
        log.info("Video session created successfully: {}", saved.getId());

        return videoSessionMapper.toDto(saved);
    }

    @Transactional
    public RoomJoinResponse joinSession(String sessionId, String userId, ParticipantRole role) {
        log.info("User {} joining session {} as {}", userId, sessionId, role);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Check if session can be joined
        if (!session.canJoin()) {
            LocalDateTime joinWindowStart = session.getScheduledStartTime().minusMinutes(15);
            throw new IllegalStateException(String.format(
                    "Session cannot be joined yet. Join window opens at %s (15 minutes before class starts at %s)",
                    joinWindowStart, session.getScheduledStartTime()
            ));
        }

        // Validate user is authorized to join
        validateUserAccess(session, userId, role);

        // ✅ Generate Agora auth token (returns token + UID)
        AgoraTokenResponse tokenResponse = agoraClient.generateAuthToken(
                session.getHundredMsRoomId(),  // Channel name
                userId,
                role.name()
        );

        log.info("✅ Agora token generated - UID: {}, Token: {}...",
                tokenResponse.getUid(),
                tokenResponse.getToken().substring(0, 20));

        // Add participant
        SessionParticipant participant = SessionParticipant.builder()
                .userId(userId)
                .role(role)
                .hundredMsPeerId(userId)  // Agora uses userId as peer ID
                .joinedAt(LocalDateTime.now())
                .isCameraEnabled(role != ParticipantRole.PARENT_OBSERVER)
                .isMicEnabled(role != ParticipantRole.PARENT_OBSERVER)
                .isScreenSharing(false)
                .build();

        session.addParticipant(participant);

        // Start session if not already started
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.startSession();
        }

        videoSessionRepository.save(session);

        // ✅ Return response with agoraUid
        return RoomJoinResponse.builder()
                .sessionId(session.getId())
                .roomId(session.getHundredMsRoomId())
                .roomName(session.getHundredMsRoomName())
                .authToken(tokenResponse.getToken())      // ✅ Use token from response
                .agoraUid(tokenResponse.getUid())         // ✅ Include UID for frontend
                .peerId(userId)
                .role(role.name())
                .roomConfig(videoSessionMapper.toRoomConfigDto(session.getMetadata()))
                .webrtcUrl("wss://agora-rtc.io")
                .expiresAt(tokenResponse.getExpiresAt()) // ✅ Include expiration
                .build();
    }


    @Transactional
    public VideoSessionDto endSession(String sessionId, String userId) {
        log.info("Ending session {} by user {}", sessionId, userId);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.isActive()) {
            throw new IllegalStateException("Session is not active");
        }

        // Validate user can end session (teacher only)
        if (!userId.equals(session.getTeacherId())) {
            throw new IllegalArgumentException("Only teacher can end the session");
        }

        // End all active participants
        session.getParticipants().stream()
                .filter(SessionParticipant::isActive)
                .forEach(SessionParticipant::leave);

        // End session
        session.endSession();

        // Stop recording if enabled
        if (Boolean.TRUE.equals(session.getRecordingEnabled()) && session.getRecordingId() != null) {
            agoraClient.stopRecording(session.getRecordingId());
        }

        VideoSession saved = videoSessionRepository.save(session);
        log.info("Session ended successfully: {}", saved.getId());

        return videoSessionMapper.toDto(saved);
    }

    public VideoSessionDto getSessionById(String sessionId) {
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return videoSessionMapper.toDto(session);
    }

    public VideoSessionDto getSessionByClassId(String classSessionId) {
        log.info("🔍 Finding video session for class: {}", classSessionId);

        VideoSession session = videoSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> {
                    log.error("❌ Session not found for class: {}", classSessionId);
                    return new IllegalArgumentException("Video session not found for class: " + classSessionId);
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
            throw new RuntimeException("Failed to convert session to DTO", e);
        }
    }



    public Page<VideoSessionDto> getTeacherSessions(String teacherId, Pageable pageable) {
        return videoSessionRepository.findByTeacherId(teacherId, pageable)
                .map(videoSessionMapper::toDto);
    }

    public Page<VideoSessionDto> getStudentSessions(String studentId, Pageable pageable) {
        return videoSessionRepository.findByStudentId(studentId, pageable)
                .map(videoSessionMapper::toDto);
    }

    public List<VideoSessionDto> getActiveSessions() {
        return videoSessionRepository.findByStatusAndScheduledStartTimeBefore(
                        SessionStatus.IN_PROGRESS,
                        LocalDateTime.now())
                .stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

    @Transactional
    public void startRecording(String sessionId) {
        log.info("Starting recording for session: {}", sessionId);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.isActive()) {
            throw new IllegalStateException("Session must be active to start recording");
        }

        String recordingId = agoraClient.startRecording(session.getHundredMsRoomId());
        session.setRecordingId(recordingId);
        session.setRecordingStatus("RECORDING");

        videoSessionRepository.save(session);
        log.info("Recording started with ID: {}", recordingId);
    }

    private String generateRoomName(RoomCreateRequest request) {
        return String.format("class_%s_%s",
                request.getClassSessionId(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    private void validateUserAccess(VideoSession session, String userId, ParticipantRole role) {
        boolean hasAccess = switch (role) {
            case TEACHER -> userId.equals(session.getTeacherId());
            case STUDENT -> userId.equals(session.getStudentId());
            case PARENT_OBSERVER -> userId.equals(session.getParentId());
        };

        if (!hasAccess) {
            throw new IllegalArgumentException("User not authorized to join this session");
        }
    }

    public boolean existsByClassSessionId(String classSessionId) {
        return videoSessionRepository.existsByClassSessionId(classSessionId);
    }

}
