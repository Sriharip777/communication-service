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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSessionService {

    private final VideoSessionRepository videoSessionRepository;
    private final AgoraClient agoraClient;
    private final VideoSessionMapper videoSessionMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ==================== CREATE ROOM ====================

    @Transactional
    public VideoSessionDto createRoom(RoomCreateRequest request) {
        log.info("🎥 Creating video session for classSessionId={}, bookingId={}",
                request.getClassSessionId(), request.getBookingId());

        // ── Duplicate guard: by classSessionId ───────────────────────────
        if (videoSessionRepository.existsByClassSessionId(
                request.getClassSessionId())) {
            log.warn("⚠️ Session already exists for classSessionId={}. " +
                    "Returning existing.", request.getClassSessionId());
            return videoSessionRepository
                    .findByClassSessionId(request.getClassSessionId())
                    .map(videoSessionMapper::toDto)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inconsistent state: session exists but not found"
                    ));
        }

        // ── Duplicate guard: by bookingId ────────────────────────────────
        if (request.getBookingId() != null
                && !request.getBookingId().isBlank()
                && videoSessionRepository.existsByBookingId(
                request.getBookingId())) {
            log.warn("⚠️ Session already exists for bookingId={}. " +
                    "Returning existing.", request.getBookingId());
            return videoSessionRepository
                    .findByBookingId(request.getBookingId())
                    .map(videoSessionMapper::toDto)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Inconsistent state: bookingId exists but not found"
                    ));
        }

        // ── Sanitize fields ───────────────────────────────────────────────
        String sanitizedBookingId = (request.getBookingId() != null
                && !request.getBookingId().isBlank())
                ? request.getBookingId() : null;

        String sanitizedParentId = (request.getParentId() != null
                && !request.getParentId().isBlank())
                ? request.getParentId() : null;

        // ── Channel name ──────────────────────────────────────────────────
        String roomNameRaw = (request.getChannelName() != null
                && !request.getChannelName().isBlank())
                ? request.getChannelName()
                : generateRoomName(request);

        String channelName = agoraClient.createRoom(
                roomNameRaw,
                request.getDurationMinutes() != null
                        ? request.getDurationMinutes() : 60
        );

        // ── Calculate end time ────────────────────────────────────────────
        LocalDateTime endTime = request.getScheduledEndTime();
        if (endTime == null
                && request.getScheduledStartTime() != null
                && request.getDurationMinutes() != null) {
            endTime = request.getScheduledStartTime()
                    .plusMinutes(request.getDurationMinutes());
        }

        // ── Build entity ──────────────────────────────────────────────────
        VideoSession session = VideoSession.builder()
                .classSessionId(request.getClassSessionId())
                .bookingId(sanitizedBookingId)
                .teacherId(request.getTeacherId())
                .studentId(request.getStudentId())
                .parentId(sanitizedParentId)
                .hundredMsRoomId(channelName)
                .hundredMsRoomName(roomNameRaw)
                .status(SessionStatus.SCHEDULED)
                .scheduledStartTime(request.getScheduledStartTime())
                .scheduledEndTime(endTime)
                .durationMinutes(request.getDurationMinutes())
                .recordingEnabled(request.getRecordingEnabled())
                .participants(new ArrayList<>())
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

        log.info("✅ Video session created:");
        log.info("   🆔 Session ID:  {}", saved.getId());
        log.info("   📋 Booking ID:  {}", saved.getBookingId());
        log.info("   🎓 Class ID:    {}", saved.getClassSessionId());
        log.info("   👨‍🏫 Teacher:     {}", saved.getTeacherId());
        log.info("   👨‍🎓 Student:     {}", saved.getStudentId());
        log.info("   📹 Channel:     {}", saved.getHundredMsRoomId());
        log.info("   📅 Start:       {}", saved.getScheduledStartTime());
        log.info("   📅 End:         {}", saved.getScheduledEndTime());
        log.info("   🔓 canJoin now: {}", saved.canJoin());

        return videoSessionMapper.toDto(saved);
    }

    // ==================== JOIN SESSION ====================

    @Transactional
    public RoomJoinResponse joinSession(
            String sessionId, String userId, ParticipantRole role) {

        log.info("🚪 User {} joining session {} as {}", userId, sessionId, role);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));

        if (!session.canJoin()) {
            LocalDateTime joinWindowStart = session.getScheduledStartTime() != null
                    ? session.getScheduledStartTime().minusMinutes(15)
                    : null;
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format(
                            "Session cannot be joined yet. " +
                                    "Join window opens at %s " +
                                    "(15 min before %s). Status: %s",
                            joinWindowStart,
                            session.getScheduledStartTime(),
                            session.getStatus()
                    )
            );
        }

        validateUserAccess(session, userId, role);

        if (session.getParticipants() == null) {
            session.setParticipants(new ArrayList<>());
        }

        // ── Already joined: return fresh token ───────────────────────────
        boolean alreadyJoined = session.getParticipants().stream()
                .anyMatch(p -> userId.equals(p.getUserId())
                        && p.getLeftAt() == null);

        if (alreadyJoined) {
            log.warn("⚠️ User {} already in session {}, issuing fresh token",
                    userId, sessionId);
            AgoraTokenResponse tokenResponse = agoraClient.generateAuthToken(
                    session.getHundredMsRoomId(), userId, role.name());
            return buildJoinResponse(session, tokenResponse, role, userId);
        }

        // ── Generate token ────────────────────────────────────────────────
        AgoraTokenResponse tokenResponse = agoraClient.generateAuthToken(
                session.getHundredMsRoomId(), userId, role.name());

        log.info("✅ Agora token generated — UID: {}", tokenResponse.getUid());

        // ── Add participant ───────────────────────────────────────────────
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

        // ── Auto-start session on first join ──────────────────────────────
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.startSession();
            log.info("▶️ Session auto-started: {}", sessionId);
        }

        videoSessionRepository.save(session);
        log.info("✅ User {} joined session {} as {}", userId, sessionId, role);

        return buildJoinResponse(session, tokenResponse, role, userId);
    }

    // ==================== END SESSION ====================

    @Transactional
    public VideoSessionDto endSession(String sessionId, String userId) {
        log.info("🛑 Ending session {} by user {}", sessionId, userId);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));

        if (!session.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session is not active. Current status: " + session.getStatus()
            );
        }

        if (!userId.equals(session.getTeacherId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the teacher can end the session"
            );
        }

        // ── Mark all participants as left ─────────────────────────────────
        if (session.getParticipants() != null) {
            session.getParticipants().stream()
                    .filter(SessionParticipant::isActive)
                    .forEach(SessionParticipant::leave);
        }

        session.endSession();

        // ── Stop cloud recording and get real GCS URL ─────────────────────
        // ── Stop cloud recording and get real GCS URL ─────────────────────
        String realVideoUrl = null;

        if (Boolean.TRUE.equals(session.getRecordingEnabled())
                && session.getRecordingId() != null) {

            log.info("🎙️ Stopping cloud recording: {}", session.getRecordingId());

            // ✅ FIX: Pass channelName (hundredMsRoomId) to stopRecording
            realVideoUrl = agoraClient.stopRecording(
                    session.getRecordingId(),
                    session.getHundredMsRoomId()  // ← ADD THIS
            );

            if (realVideoUrl != null) {
                session.setRecordingUrl(realVideoUrl);
                session.setRecordingStatus("READY");
                log.info("✅ Real recording URL saved: {}", realVideoUrl);
            } else {
                session.setRecordingStatus("PROCESSING");
                log.warn("⚠️ No video URL from Agora yet. Status set to PROCESSING.");
            }
        }

        VideoSession saved = videoSessionRepository.save(session);
        log.info("✅ Session ended: {}, duration: {} min",
                saved.getId(), saved.getActualDurationMinutes());

        // ✅ Publish RECORDING_READY Kafka event to content-service
        if (realVideoUrl != null) {
            publishRecordingReady(saved, realVideoUrl);
        }

        return videoSessionMapper.toDto(saved);
    }

    // ==================== GET BY ID ====================

    @Transactional
    public VideoSessionDto getSessionById(String sessionId) {
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId
                ));

        refreshExpiredSessionStatus(session);
        return videoSessionMapper.toDto(session);
    }

    // ==================== GET BY CLASS ID ====================
    @Transactional
    public VideoSessionDto getSessionByClassId(String classSessionId) {
        log.info("🔍 Finding session for classSessionId: {}", classSessionId);

        VideoSession session = videoSessionRepository
                .findByClassSessionId(classSessionId)
                .orElseThrow(() -> {
                    log.error("❌ No session for classSessionId: {}", classSessionId);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Video session not found for class: " + classSessionId
                    );
                });

        refreshExpiredSessionStatus(session);

        log.info("✅ Session found: id={}, status={}, canJoin={}",
                session.getId(), session.getStatus(), session.canJoin());

        return videoSessionMapper.toDto(session);
    }
    // ==================== GET BY BOOKING ID ====================
    @Transactional
    public VideoSessionDto getSessionByBookingId(String bookingId) {
        log.info("🔍 Finding session for bookingId: {}", bookingId);

        VideoSession session = videoSessionRepository
                .findByBookingId(bookingId)
                .orElseThrow(() -> {
                    log.error("❌ No session found for bookingId: {}", bookingId);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Video session not found for booking: " + bookingId
                    );
                });

        refreshExpiredSessionStatus(session);

        log.info("✅ Session found by bookingId: id={}, status={}, canJoin={}",
                session.getId(), session.getStatus(), session.canJoin());

        return videoSessionMapper.toDto(session);
    }

    // ==================== GET TEACHER SESSIONS ====================

    @Transactional
    public List<VideoSessionDto> getTeacherSessions(String teacherId) {
        log.info("📋 Fetching all sessions for teacher: {}", teacherId);

        List<VideoSession> sessions = videoSessionRepository
                .findByTeacherIdOrderByScheduledStartTimeDesc(teacherId);

        sessions.forEach(this::refreshExpiredSessionStatus);

        log.info("✅ Found {} sessions for teacher {}", sessions.size(), teacherId);
        return sessions.stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

    // ==================== GET STUDENT SESSIONS ====================
    @Transactional
    public List<VideoSessionDto> getStudentSessions(String studentId) {
        log.info("📋 Fetching all sessions for student: {}", studentId);

        List<VideoSession> sessions = videoSessionRepository
                .findByStudentIdOrderByScheduledStartTimeDesc(studentId);

        sessions.forEach(this::refreshExpiredSessionStatus);

        log.info("✅ Found {} sessions for student {}", sessions.size(), studentId);
        return sessions.stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

// ==================== GET ACTIVE SESSIONS ====================

    public List<VideoSessionDto> getActiveSessions() {
        return videoSessionRepository
                .findByStatusAndScheduledStartTimeBefore(
                        SessionStatus.IN_PROGRESS,
                        LocalDateTime.now())
                .stream()
                .map(videoSessionMapper::toDto)
                .toList();
    }

// ==================== START RECORDING ====================

    @Transactional
    public void startRecording(String sessionId) {
        log.info("🎙️ Starting recording for session: {}", sessionId);

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

        // ✅ startRecording now calls real Agora Cloud Recording API
        // Returns "resourceId:sid" if configured, else stub "agora_rec_UUID"
        String recordingId = agoraClient.startRecording(
                session.getHundredMsRoomId());

        session.setRecordingId(recordingId);
        session.setRecordingStatus("RECORDING");

        videoSessionRepository.save(session);
        log.info("✅ Recording started with ID: {}", recordingId);
    }

// ==================== UTILITY ====================

    public boolean existsByClassSessionId(String classSessionId) {
        return videoSessionRepository.existsByClassSessionId(classSessionId);
    }

// ==================== PRIVATE: PUBLISH RECORDING READY ================

    private void publishRecordingReady(VideoSession session, String videoUrl) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "RECORDING_READY");
            event.put("sessionId", session.getId());
            event.put("bookingId", session.getBookingId());
            event.put("classSessionId", session.getClassSessionId());
            event.put("teacherId", session.getTeacherId());
            event.put("studentId", session.getStudentId());
            event.put("channelName", session.getHundredMsRoomId());
            event.put("recordingUrl", videoUrl);
            event.put("durationMinutes", session.getActualDurationMinutes());

            kafkaTemplate.send("session-events", session.getId(), event);

            log.info("📨 RECORDING_READY event published for session: {}",
                    session.getId());
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish RECORDING_READY event: {}",
                    e.getMessage());
        }
    }

// ==================== PRIVATE: GENERATE ROOM NAME ====================

    private String generateRoomName(RoomCreateRequest request) {
        return String.format("class_%s_%s",
                request.getClassSessionId(),
                UUID.randomUUID().toString().substring(0, 8));
    }

// ==================== PRIVATE: VALIDATE USER ACCESS ==================

    private void validateUserAccess(
            VideoSession session, String userId, ParticipantRole role) {

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
                if (session.getParentId() == null
                        || session.getParentId().isBlank()) {
                    log.info("✅ Parent {} authorized " +
                            "(no specific parentId set on session)", userId);
                    yield true;
                }
                log.warn("❌ Parent {} not authorized. Session parentId: {}",
                        userId, session.getParentId());
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

// ==================== PRIVATE: BUILD JOIN RESPONSE ===================

    private RoomJoinResponse buildJoinResponse(
            VideoSession session,
            AgoraTokenResponse tokenResponse,
            ParticipantRole role,
            String userId
    ) {
        return RoomJoinResponse.builder()
                .sessionId(session.getId())
                .roomId(session.getHundredMsRoomId())
                .roomName(session.getHundredMsRoomName())
                .authToken(tokenResponse.getToken())
                .agoraUid(tokenResponse.getUid())
                .peerId(userId)
                .role(role.name())
                .roomConfig(videoSessionMapper.toRoomConfigDto(
                        session.getMetadata()))
                .webrtcUrl("wss://agora-rtc.io")
                .expiresAt(tokenResponse.getExpiresAt())
                .build();
    }

    private void refreshExpiredSessionStatus(VideoSession session) {
        if (session == null) return;

        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELLED
                || session.getStatus() == SessionStatus.NO_SHOW) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime end = session.getScheduledEndTime();
        if (end == null
                && session.getScheduledStartTime() != null
                && session.getDurationMinutes() != null) {
            end = session.getScheduledStartTime().plusMinutes(session.getDurationMinutes());
        }

        if (end == null) {
            return;
        }

        if (now.isAfter(end)) {
            boolean anyoneJoined = session.getParticipants() != null
                    && !session.getParticipants().isEmpty();

            if (session.getStatus() == SessionStatus.SCHEDULED) {
                if (anyoneJoined) {
                    if (session.getParticipants() != null) {
                        session.getParticipants().stream()
                                .filter(SessionParticipant::isActive)
                                .forEach(SessionParticipant::leave);
                    }

                    session.endSession();
                    log.info("✅ Expired scheduled session marked COMPLETED: {}", session.getId());
                } else {
                    session.setStatus(SessionStatus.NO_SHOW);
                    session.setEndTime(now);
                    log.info("✅ Expired scheduled session marked NO_SHOW: {}", session.getId());
                }
                videoSessionRepository.save(session);
                return;
            }

            if (session.getStatus() == SessionStatus.IN_PROGRESS) {
                if (session.getParticipants() != null) {
                    session.getParticipants().stream()
                            .filter(SessionParticipant::isActive)
                            .forEach(SessionParticipant::leave);
                }

                session.endSession();
                videoSessionRepository.save(session);
                log.info("✅ Expired in-progress session auto-marked COMPLETED: {}", session.getId());
            }
        }
    }

}