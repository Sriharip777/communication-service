package com.tcon.communication_service.video.service;

import com.tcon.communication_service.video.integration.HundredMsClient;
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
    private final HundredMsClient hundredMsClient;
    private final VideoSessionMapper videoSessionMapper;

    /**
     * Create a new video session room
     */
    @Transactional
    public VideoSessionDto createRoom(RoomCreateRequest  request) {
        log.info("Creating video session room for class: {}", request.getClassSessionId());

        // Check if session already exists
        if (videoSessionRepository.existsByClassSessionId(request.getClassSessionId())) {
            throw new IllegalStateException("Video session already exists for this class");
        }

        // Create room in 100ms
        String roomName = generateRoomName(request);
        String roomId = hundredMsClient.createRoom(roomName, request.getDurationMinutes());

        // Build video session entity
        VideoSession session = VideoSession.builder()
                .classSessionId(request.getClassSessionId())
                .teacherId(request.getTeacherId())
                .studentId(request.getStudentId())
                .parentId(request.getParentId())
                .hundredMsRoomId(roomId)
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
        log.info("Video session created successfully: {}", saved.getId());

        return videoSessionMapper.toDto(saved);
    }

    /**
     * Join a video session
     */
    @Transactional
    public RoomJoinResponse joinSession(String sessionId, String userId, ParticipantRole role) {
        log.info("User {} joining session {} as {}", userId, sessionId, role);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.canJoin()) {
            throw new IllegalStateException("Session cannot be joined at this time");
        }

        // Validate user is authorized to join
        validateUserAccess(session, userId, role);

        // Generate 100ms auth token
        String authToken = hundredMsClient.generateAuthToken(
                session.getHundredMsRoomId(),
                userId,
                role.name()
        );

        // Add participant
        SessionParticipant participant = SessionParticipant.builder()
                .userId(userId)
                .role(role)
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

        return RoomJoinResponse.builder()
                .sessionId(session.getId())
                .roomId(session.getHundredMsRoomId())
                .roomName(session.getHundredMsRoomName())
                .authToken(authToken)
                .peerId(UUID.randomUUID().toString())
                .role(role.name())
                .roomConfig(videoSessionMapper.toRoomConfigDto(session.getMetadata()))
                .webrtcUrl("wss://prod-in2.100ms.live/ws")
                .build();
    }

    /**
     * End a video session
     */
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
            hundredMsClient.stopRecording(session.getRecordingId());
        }

        VideoSession saved = videoSessionRepository.save(session);
        log.info("Session ended successfully: {}", saved.getId());

        return videoSessionMapper.toDto(saved);
    }

    /**
     * Get session by ID
     */
    public VideoSessionDto getSessionById(String sessionId) {
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return videoSessionMapper.toDto(session);
    }

    /**
     * Get sessions by teacher
     */
    public Page<VideoSessionDto> getTeacherSessions(String teacherId, Pageable pageable) {
        return videoSessionRepository.findByTeacherId(teacherId, pageable)
                .map(videoSessionMapper::toDto);
    }

    /**
     * Get sessions by student
     */
    public Page<VideoSessionDto> getStudentSessions(String studentId, Pageable pageable) {
        return videoSessionRepository.findByStudentId(studentId, pageable)
                .map(videoSessionMapper::toDto);
    }

    /**
     * Get active sessions
     */
    public List<VideoSessionDto> getActiveSessions() {
        return videoSessionRepository.findByStatusAndScheduledStartTimeBefore(
                        SessionStatus.IN_PROGRESS,
                        LocalDateTime.now())
                .stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

    /**
     * Start recording
     */
    @Transactional
    public void startRecording(String sessionId) {
        log.info("Starting recording for session: {}", sessionId);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.isActive()) {
            throw new IllegalStateException("Session must be active to start recording");
        }

        String recordingId = hundredMsClient.startRecording(session.getHundredMsRoomId());
        session.setRecordingId(recordingId);
        session.setRecordingStatus("RECORDING");

        videoSessionRepository.save(session);
        log.info("Recording started with ID: {}", recordingId);
    }

    /**
     * Private helper methods
     */
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
}
