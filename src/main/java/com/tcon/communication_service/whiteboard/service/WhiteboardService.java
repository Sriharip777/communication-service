package com.tcon.communication_service.whiteboard.service;

import com.tcon.communication_service.config.AgoraWhiteboardConfig;
import com.tcon.communication_service.video.entity.ParticipantRole;
import com.tcon.communication_service.video.entity.SessionType;
import com.tcon.communication_service.video.entity.VideoSession;
import com.tcon.communication_service.video.repository.VideoSessionRepository;
import com.tcon.communication_service.whiteboard.client.AgoraWhiteboardClient;
import com.tcon.communication_service.whiteboard.dto.*;
import com.tcon.communication_service.whiteboard.entity.WhiteboardRoom;
import com.tcon.communication_service.whiteboard.entity.WhiteboardSnapshot;
import com.tcon.communication_service.whiteboard.repository.WhiteboardRoomRepository;
import com.tcon.communication_service.whiteboard.repository.WhiteboardSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhiteboardService {

    private final AgoraWhiteboardClient agoraClient;
    private final WhiteboardRoomRepository whiteboardRoomRepository;
    private final WhiteboardSnapshotRepository snapshotRepository;
    private final VideoSessionRepository videoSessionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgoraWhiteboardConfig config;

    private static final String WHITEBOARD_ROOM_CACHE_PREFIX = "whiteboard:room:";
    private static final String WHITEBOARD_STATE_PREFIX = "whiteboard:state:";

    /**
     * ✅ Open whiteboard within active video session
     * Called when teacher clicks "Open Whiteboard" during video call
     */
    @Transactional
    public Mono<WhiteboardOpenResponse> openWhiteboardInSession(WhiteboardOpenRequest request) {
        log.info("📋 Opening whiteboard for class session: {}", request.getClassSessionId());

        // ✅ Validate video session exists and is active
        VideoSession videoSession = videoSessionRepository
                .findByClassSessionId(request.getClassSessionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Video session not found: " + request.getClassSessionId()));

        if (!videoSession.isActive()) {
            throw new IllegalStateException("Video session is not active");
        }

        // Check if whiteboard already open
        Optional<WhiteboardRoom> existing = whiteboardRoomRepository
                .findByClassSessionId(request.getClassSessionId());

        if (existing.isPresent() && existing.get().getIsActive()) {
            log.info("ℹ️ Whiteboard already open for session: {}", request.getClassSessionId());
            return getRoomResponse(existing.get());
        }

        // ✅ Determine limit based on session type
        SessionType sessionType = request.getSessionType() != null
                ? request.getSessionType()
                : SessionType.SOLO;

        Integer limit = calculateRoomLimit(sessionType, request);

        return agoraClient.createRoom(limit)
                .flatMap(roomData -> {
                    String uuid = (String) roomData.get("uuid");

                    // Generate admin token for teacher
                    return agoraClient.generateRoomToken(uuid, "admin")
                            .map(roomToken -> {
                                // Save to MongoDB
                                WhiteboardRoom room = WhiteboardRoom.builder()
                                        .classSessionId(request.getClassSessionId())
                                        .courseId(request.getCourseId())
                                        .agoraWhiteboardUuid(uuid)
                                        .teamUUID((String) roomData.get("teamUUID"))
                                        .appUUID((String) roomData.get("appUUID"))
                                        .teacherId(request.getTeacherId())
                                        .teacherName(request.getTeacherName())
                                        .sessionType(sessionType)
                                        .studentId(request.getStudentId())
                                        .enrolledStudentIds(request.getEnrolledStudentIds())
                                        .activeStudentIds(List.of())
                                        .isActive(true)
                                        .isBan(false)
                                        .createdAt(LocalDateTime.now())
                                        .expiresAt(LocalDateTime.now().plusHours(
                                                config.getTokenExpiryHours()))
                                        .snapshotCount(0)
                                        .build();

                                WhiteboardRoom savedRoom = whiteboardRoomRepository.save(room);

                                // Build response
                                WhiteboardOpenResponse response = WhiteboardOpenResponse.builder()
                                        .whiteboardRoomId(savedRoom.getId())
                                        .classSessionId(request.getClassSessionId())
                                        .agoraWhiteboardUuid(uuid)
                                        .roomToken(roomToken)
                                        .appId(config.getAppId())
                                        .region(config.getRegion())
                                        .expiresAt(System.currentTimeMillis() +
                                                (config.getTokenExpiryHours() * 3600 * 1000L))
                                        .isActive(true)
                                        .build();

                                // Cache in Redis
                                cacheRoomResponse(request.getClassSessionId(), response);

                                log.info("✅ Whiteboard opened: uuid={}, session={}",
                                        uuid, request.getClassSessionId());
                                return response;
                            });
                });
    }

    /**
     * ✅ Student/Parent accesses whiteboard
     * Auto-authorized if they're in the video session
     */
    @Transactional
    public Mono<WhiteboardAccessResponse> accessWhiteboardInSession(
            WhiteboardAccessRequest request) {

        log.info("🎫 User {} accessing whiteboard in session: {}",
                request.getUserId(), request.getClassSessionId());

        // Find whiteboard room
        WhiteboardRoom room = whiteboardRoomRepository
                .findByClassSessionId(request.getClassSessionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Whiteboard not opened for session: " + request.getClassSessionId()));

        if (!room.getIsActive()) {
            throw new IllegalStateException("Whiteboard is not active");
        }

        // ✅ Authorization check using ParticipantRole
        String agoraRole = authorizeAndGetRole(room, request.getUserId(), request.getRole());

        // Track active student (exclude teacher and observers)
        if (request.getRole() == ParticipantRole.STUDENT) {
            room.getActiveStudentIds().add(request.getUserId());
            whiteboardRoomRepository.save(room);
        }

        return agoraClient.generateRoomToken(room.getAgoraWhiteboardUuid(), agoraRole)
                .map(token -> WhiteboardAccessResponse.builder()
                        .agoraWhiteboardUuid(room.getAgoraWhiteboardUuid())
                        .roomToken(token)
                        .role(agoraRole)
                        .appId(config.getAppId())
                        .region(config.getRegion())
                        .expiresAt(System.currentTimeMillis() +
                                (config.getTokenExpiryHours() * 3600 * 1000L))
                        .build());
    }

    /**
     * ✅ Save whiteboard snapshot (Page 1, Page 2, etc.)
     */
    @Transactional
    public Mono<WhiteboardSnapshotResponse> saveSnapshot(
            WhiteboardSnapshotSaveRequest request) {

        log.info("💾 Saving snapshot for session: {}", request.getClassSessionId());

        WhiteboardRoom room = whiteboardRoomRepository
                .findByClassSessionId(request.getClassSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Whiteboard room not found"));

        // Generate snapshot name
        int snapshotNumber = room.getSnapshotCount() + 1;
        String snapshotName = request.getSnapshotName() != null
                ? request.getSnapshotName()
                : "Page " + snapshotNumber;

        // Determine authorized students
        List<String> authorizedStudents = getAuthorizedStudents(room);

        // Save snapshot
        WhiteboardSnapshot snapshot = WhiteboardSnapshot.builder()
                .classSessionId(request.getClassSessionId())
                .courseId(room.getCourseId())
                .agoraWhiteboardUuid(room.getAgoraWhiteboardUuid())
                .snapshotName(snapshotName)
                .snapshotData(request.getSnapshotData())
                .teacherId(room.getTeacherId())
                .teacherName(room.getTeacherName())
                .authorizedStudentIds(authorizedStudents)
                .snapshotNumber(snapshotNumber)
                .createdAt(LocalDateTime.now())
                .build();

        WhiteboardSnapshot saved = snapshotRepository.save(snapshot);

        // Update room snapshot count
        room.setSnapshotCount(snapshotNumber);
        whiteboardRoomRepository.save(room);

        log.info("✅ Snapshot saved: {} for session: {}",
                snapshotName, request.getClassSessionId());

        return Mono.just(WhiteboardSnapshotResponse.builder()
                .id(saved.getId())
                .classSessionId(saved.getClassSessionId())
                .snapshotName(saved.getSnapshotName())
                .snapshotNumber(saved.getSnapshotNumber())
                .createdAt(saved.getCreatedAt())
                .build());
    }

    /**
     * ✅ Get snapshots for a specific class session
     */
    public Mono<List<WhiteboardSnapshot>> getSessionSnapshots(
            String classSessionId, String userId) {

        log.info("📖 Getting snapshots for session: {} by user: {}",
                classSessionId, userId);

        List<WhiteboardSnapshot> snapshots = snapshotRepository
                .findByClassSessionIdOrderBySnapshotNumberAsc(classSessionId);

        // Filter by authorization
        List<WhiteboardSnapshot> authorized = snapshots.stream()
                .filter(s -> isUserAuthorized(s, userId))
                .toList();

        return Mono.just(authorized);
    }

    /**
     * ✅ Get all snapshots for a course (GROUP classes)
     */
    public Mono<List<WhiteboardSnapshot>> getCourseSnapshots(
            String courseId, String studentId) {

        log.info("📚 Getting course snapshots for: {} by student: {}",
                courseId, studentId);

        List<WhiteboardSnapshot> snapshots = snapshotRepository
                .findByCourseIdAndAuthorizedStudentIdsContaining(courseId, studentId);

        return Mono.just(snapshots);
    }

    /**
     * Update whiteboard state (real-time backup)
     */
    public void updateState(String classSessionId, String stateJson) {
        String redisKey = WHITEBOARD_STATE_PREFIX + classSessionId;
        redisTemplate.opsForValue().set(
                redisKey,
                stateJson,
                config.getTokenExpiryHours(),
                TimeUnit.HOURS
        );
        log.debug("💾 Updated whiteboard state for session: {}", classSessionId);
    }

    /**
     * Get whiteboard state
     */
    public String getState(String classSessionId) {
        String redisKey = WHITEBOARD_STATE_PREFIX + classSessionId;
        Object state = redisTemplate.opsForValue().get(redisKey);
        return state != null ? state.toString() : "{}";
    }

    /**
     * ✅ Close whiteboard - with permanent flag
     * @param classSessionId - the class session ID
     * @param permanent - true for manual end, false for network disconnect
     */
    @Transactional
    public Mono<Void> closeWhiteboard(String classSessionId, boolean permanent) {
        if (!permanent) {
            log.info("⚠️ Temporary disconnect - keeping whiteboard active for session: {}", classSessionId);
            return Mono.empty(); // Don't close whiteboard
        }

        log.info("🔴 Permanently closing whiteboard for session: {}", classSessionId);

        Optional<WhiteboardRoom> roomOpt = whiteboardRoomRepository
                .findByClassSessionId(classSessionId);

        if (roomOpt.isEmpty()) {
            log.warn("⚠️ No whiteboard to close for session: {}", classSessionId);
            return Mono.empty();
        }

        WhiteboardRoom room = roomOpt.get();

        return agoraClient.banRoom(room.getAgoraWhiteboardUuid())
                .doOnSuccess(v -> {
                    // Update database
                    room.setIsActive(false);
                    room.setIsBan(true);
                    room.setClosedAt(LocalDateTime.now());
                    whiteboardRoomRepository.save(room);

                    // Clean up cache
                    String cacheKey = WHITEBOARD_ROOM_CACHE_PREFIX + classSessionId;
                    redisTemplate.delete(cacheKey);
                    redisTemplate.delete(WHITEBOARD_STATE_PREFIX + classSessionId);

                    log.info("✅ Whiteboard permanently closed for session: {}", classSessionId);
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to ban room in Agora: {}", error.getMessage());
                    // Still mark as closed in database even if Agora fails
                    room.setIsActive(false);
                    room.setClosedAt(LocalDateTime.now());
                    whiteboardRoomRepository.save(room);
                    return Mono.empty();
                });
    }

    /**
     * ✅ Keep existing method for backward compatibility (defaults to permanent)
     */
    @Transactional
    public Mono<Void> closeWhiteboard(String classSessionId) {
        return closeWhiteboard(classSessionId, true);
    }

    /**
     * ✅ Handle temporary network disconnect - keep whiteboard alive
     */
    @Transactional
    public Mono<Void> handleDisconnect(String classSessionId, String userId) {
        log.info("⚠️ User {} disconnected from session {} - whiteboard remains active",
                userId, classSessionId);

        Optional<WhiteboardRoom> roomOpt = whiteboardRoomRepository
                .findByClassSessionId(classSessionId);

        if (roomOpt.isPresent()) {
            WhiteboardRoom room = roomOpt.get();

            // Track last disconnect time for cleanup
            room.setLastDisconnectTime(LocalDateTime.now());
            whiteboardRoomRepository.save(room);

            log.info("✅ Disconnect tracked for session: {}", classSessionId);
        }

        return Mono.empty();
    }

    /**
     * ✅ Cleanup abandoned sessions (scheduled task)
     * Call this from a @Scheduled method in a separate component
     */
    @Transactional
    public void cleanupAbandonedSessions(int abandonedMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(abandonedMinutes);

        log.info("🧹 Checking for abandoned sessions (threshold: {} min ago)", abandonedMinutes);

        List<WhiteboardRoom> abandonedRooms = whiteboardRoomRepository
                .findByIsActiveTrueAndLastDisconnectTimeBefore(threshold);

        if (abandonedRooms.isEmpty()) {
            log.debug("✅ No abandoned sessions found");
            return;
        }

        log.warn("⚠️ Found {} abandoned sessions to cleanup", abandonedRooms.size());

        abandonedRooms.forEach(room -> {
            log.warn("🔴 Closing abandoned session: {} (last disconnect: {})",
                    room.getClassSessionId(), room.getLastDisconnectTime());

            // Close permanently (blocking call)
            closeWhiteboard(room.getClassSessionId(), true)
                    .doOnSuccess(v -> log.info("✅ Successfully closed abandoned session: {}",
                            room.getClassSessionId()))
                    .doOnError(e -> log.error("❌ Failed to close abandoned session: {}",
                            room.getClassSessionId(), e))
                    .block();
        });
    }

    /**
     * Get single snapshot by ID with authorization check
     */
    public Mono<WhiteboardSnapshot> getSnapshotById(String snapshotId, String userId) {
        log.info("📄 Getting snapshot: {} by user: {}", snapshotId, userId);

        return Mono.fromCallable(() -> snapshotRepository.findById(snapshotId)
                        .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId)))
                .map(snapshot -> {
                    if (!isUserAuthorized(snapshot, userId)) {
                        throw new IllegalArgumentException("User not authorized to view this snapshot");
                    }
                    return snapshot;
                });
    }

    /**
     * Get whiteboard status for a session
     */
    public Mono<Map<String, Object>> getWhiteboardStatus(String classSessionId) {
        log.debug("ℹ️ Checking whiteboard status for session: {}", classSessionId);

        return Mono.fromCallable(() -> {
            Optional<WhiteboardRoom> room = whiteboardRoomRepository
                    .findByClassSessionId(classSessionId);

            if (room.isEmpty()) {
                return Map.of(
                        "isOpen", false,
                        "classSessionId", classSessionId
                );
            }

            WhiteboardRoom whiteboardRoom = room.get();
            return Map.of(
                    "isOpen", whiteboardRoom.getIsActive(),
                    "classSessionId", classSessionId,
                    "whiteboardRoomId", whiteboardRoom.getId(),
                    "agoraWhiteboardUuid", whiteboardRoom.getAgoraWhiteboardUuid(),
                    "snapshotCount", whiteboardRoom.getSnapshotCount(),
                    "activeStudents", whiteboardRoom.getActiveStudentIds().size(),
                    "sessionType", whiteboardRoom.getSessionType().name()
            );
        });
    }

    // ==================== HELPER METHODS ====================

    private Integer calculateRoomLimit(SessionType sessionType, WhiteboardOpenRequest request) {
        return switch (sessionType) {
            case SOLO, DEMO -> 2;  // Teacher + 1 student
            case RECURRING -> 2;   // Still 1-on-1
            case GROUP -> request.getEnrolledStudentIds() != null
                    ? request.getEnrolledStudentIds().size() + 1  // Students + teacher
                    : 50;  // Default
        };
    }

    private String authorizeAndGetRole(WhiteboardRoom room, String userId, ParticipantRole role) {
        return switch (role) {
            case TEACHER -> {
                if (!userId.equals(room.getTeacherId())) {
                    throw new IllegalArgumentException("User is not the teacher");
                }
                yield "admin";
            }
            case STUDENT -> {
                boolean isAuthorized = switch (room.getSessionType()) {
                    case SOLO, DEMO, RECURRING -> userId.equals(room.getStudentId());
                    case GROUP -> room.getEnrolledStudentIds() != null &&
                            room.getEnrolledStudentIds().contains(userId);
                };
                if (!isAuthorized) {
                    throw new IllegalArgumentException("Student not authorized for this session");
                }
                yield "writer";
            }
            case PARENT_OBSERVER -> "reader";  // View only
        };
    }

    private List<String> getAuthorizedStudents(WhiteboardRoom room) {
        return switch (room.getSessionType()) {
            case SOLO, DEMO, RECURRING -> room.getStudentId() != null
                    ? List.of(room.getStudentId())
                    : List.of();
            case GROUP -> room.getEnrolledStudentIds() != null
                    ? room.getEnrolledStudentIds()
                    : List.of();
        };
    }

    private boolean isUserAuthorized(WhiteboardSnapshot snapshot, String userId) {
        return userId.equals(snapshot.getTeacherId()) ||
                (snapshot.getAuthorizedStudentIds() != null &&
                        snapshot.getAuthorizedStudentIds().contains(userId));
    }

    private Mono<WhiteboardOpenResponse> getRoomResponse(WhiteboardRoom room) {
        return agoraClient.generateRoomToken(room.getAgoraWhiteboardUuid(), "admin")
                .map(token -> {
                    WhiteboardOpenResponse response = WhiteboardOpenResponse.builder()
                            .whiteboardRoomId(room.getId())
                            .classSessionId(room.getClassSessionId())
                            .agoraWhiteboardUuid(room.getAgoraWhiteboardUuid())
                            .roomToken(token)
                            .appId(config.getAppId())
                            .region(config.getRegion())
                            .expiresAt(System.currentTimeMillis() +
                                    (config.getTokenExpiryHours() * 3600 * 1000L))
                            .isActive(room.getIsActive())
                            .build();

                    cacheRoomResponse(room.getClassSessionId(), response);
                    return response;
                });
    }

    private void cacheRoomResponse(String classSessionId, WhiteboardOpenResponse response) {
        String cacheKey = WHITEBOARD_ROOM_CACHE_PREFIX + classSessionId;
        redisTemplate.opsForValue().set(
                cacheKey,
                response,
                config.getTokenExpiryHours(),
                TimeUnit.HOURS
        );
    }
}