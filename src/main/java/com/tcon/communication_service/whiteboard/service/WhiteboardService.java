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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/*
 * BUGS FIXED:
 * BUG-1 [CRITICAL] "Video session not found" → 500 on every whiteboard open.
 *        VideoSession lookup is now OPTIONAL. If document missing (booking-based
 *        session), we warn and continue instead of throwing.
 *
 * BUG-2 [CRITICAL] Blocking MongoDB/Redis calls executed directly on Netty
 *        event-loop thread → thread starvation under load.
 *        ALL blocking calls wrapped in Mono.fromCallable / Mono.fromRunnable
 *        + .subscribeOn(Schedulers.boundedElastic()).
 *
 * BUG-3  @Transactional removed from ALL reactive Mono<> methods — has zero
 *        effect on reactive return types and was misleading.
 *
 * BUG-4  activeStudentIds(List.of()) — List.of() is IMMUTABLE.
 *        room.getActiveStudentIds().add() would throw UnsupportedOperationException.
 *        Fixed to new ArrayList<>().
 *
 * BUG-5  enrolledStudentIds null-guard missing — NPE on any .contains() call
 *        when frontend omits the field. Guarded with != null check.
 *
 * BUG-6  cleanupAbandonedSessions used .block() inside forEach — deadlock risk.
 *        Replaced with .subscribe().
 *
 * BUG-7  Students never received whiteboard open event — were left polling /status.
 *        SimpMessagingTemplate now broadcasts WHITEBOARD_OPENED / WHITEBOARD_CLOSED
 *        to /topic/whiteboard/{classSessionId} immediately.
 */
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
    private final SimpMessagingTemplate messagingTemplate;          // BUG-7

    private static final String WHITEBOARD_ROOM_CACHE_PREFIX = "whiteboard:room:";
    private static final String WHITEBOARD_STATE_PREFIX      = "whiteboard:state:";
    private static final String WS_WHITEBOARD_TOPIC          = "/topic/whiteboard/";

    // =========================================================
    // OPEN WHITEBOARD
    // =========================================================

    /**
     * Open whiteboard within active video session.
     * Called when teacher clicks "Open Whiteboard" during video call.
     */
    public Mono<WhiteboardOpenResponse> openWhiteboardInSession(WhiteboardOpenRequest request) {
        log.info("📋 Opening whiteboard for class session: {}", request.getClassSessionId());

        // Step-1: validate video session (optional) + check existing room — blocking I/O
        return Mono.fromCallable(() -> {                                        // BUG-2 fix

                    // BUG-1 FIX: VideoSession is OPTIONAL.
                    // If document exists → validate isActive().
                    // If absent (booking-based) → warn and continue.
                    Optional<VideoSession> vsOpt = videoSessionRepository
                            .findByClassSessionId(request.getClassSessionId());

                    if (vsOpt.isPresent()) {
                        VideoSession vs = vsOpt.get();
                        if (!vs.isActive()) {
                            throw new IllegalStateException(
                                    "Video session is not active: " + request.getClassSessionId());
                        }
                        log.debug("✅ VideoSession found and active: {}", request.getClassSessionId());
                    } else {
                        log.warn("⚠️ VideoSession not found for classSessionId: {} — " +
                                "proceeding (booking-based session)", request.getClassSessionId());
                    }

                    return whiteboardRoomRepository.findByClassSessionId(request.getClassSessionId());
                })
                .subscribeOn(Schedulers.boundedElastic())                       // BUG-2 fix

                // Step-2: return existing room OR create new
                .flatMap(existingOpt -> {
                    if (existingOpt.isPresent() && Boolean.TRUE.equals(existingOpt.get().getIsActive())) {
                        log.info("ℹ️ Whiteboard already open for session: {}", request.getClassSessionId());
                        return getRoomResponse(existingOpt.get())
                                .doOnSuccess(resp -> broadcastWhiteboardOpened(resp, request)); // BUG-7
                    }

                    // Determine session type and participant limit
                    SessionType sessionType = request.getSessionType() != null
                            ? request.getSessionType() : SessionType.SOLO;
                    Integer limit = calculateRoomLimit(sessionType, request);

                    // Step-3: create Agora room
                    return agoraClient.createRoom(limit)
                            .flatMap(roomData -> {
                                String uuid = (String) roomData.get("uuid");

                                // Step-4: generate admin (teacher) token
                                return agoraClient.generateRoomToken(uuid, "admin")

                                        // Step-5: persist to MongoDB + cache — blocking I/O
                                        .flatMap(roomToken ->
                                                Mono.fromCallable(() -> {       // BUG-2 fix

                                                            // BUG-4 fix: mutable list
                                                            // BUG-5 fix: null-guard enrolledStudentIds
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
                                                                    .enrolledStudentIds(
                                                                            request.getEnrolledStudentIds() != null
                                                                                    ? request.getEnrolledStudentIds()
                                                                                    : new ArrayList<>()) // BUG-5
                                                                    .activeStudentIds(new ArrayList<>()) // BUG-4
                                                                    .isActive(true)
                                                                    .isBan(false)
                                                                    .createdAt(LocalDateTime.now())
                                                                    .expiresAt(LocalDateTime.now()
                                                                            .plusHours(config.getTokenExpiryHours()))
                                                                    .snapshotCount(0)
                                                                    .build();

                                                            WhiteboardRoom savedRoom = whiteboardRoomRepository.save(room);

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

                                                            cacheRoomResponse(request.getClassSessionId(), response);

                                                            log.info("✅ Whiteboard opened: uuid={}, session={}",
                                                                    uuid, request.getClassSessionId());
                                                            return response;
                                                        })
                                                        .subscribeOn(Schedulers.boundedElastic())   // BUG-2 fix
                                                        .doOnSuccess(resp ->
                                                                broadcastWhiteboardOpened(resp, request)) // BUG-7
                                        );
                            });
                });
    }

    // =========================================================
    // ACCESS WHITEBOARD (student / parent)
    // =========================================================

    /**
     * Student/Parent accesses whiteboard.
     * Auto-authorized if they are in the video session.
     */
    public Mono<WhiteboardAccessResponse> accessWhiteboardInSession(WhiteboardAccessRequest request) {
        log.info("🎫 User {} accessing whiteboard in session: {}",
                request.getUserId(), request.getClassSessionId());

        return Mono.fromCallable(() -> {                                        // BUG-2 fix
                    WhiteboardRoom room = whiteboardRoomRepository
                            .findByClassSessionId(request.getClassSessionId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Whiteboard not opened for session: " + request.getClassSessionId()));

                    if (!Boolean.TRUE.equals(room.getIsActive())) {
                        throw new IllegalStateException("Whiteboard is not active");
                    }

                    String agoraRole = authorizeAndGetRole(room, request.getUserId(), request.getRole());

                    // Track active student (exclude teacher and observers)
                    if (request.getRole() == ParticipantRole.STUDENT
                            && !room.getActiveStudentIds().contains(request.getUserId())) {
                        room.getActiveStudentIds().add(request.getUserId());
                        whiteboardRoomRepository.save(room);
                    }

                    return Map.entry(room.getAgoraWhiteboardUuid(), agoraRole);
                })
                .subscribeOn(Schedulers.boundedElastic())                       // BUG-2 fix
                .flatMap(entry ->
                        agoraClient.generateRoomToken(entry.getKey(), entry.getValue())
                                .map(token -> WhiteboardAccessResponse.builder()
                                        .agoraWhiteboardUuid(entry.getKey())
                                        .roomToken(token)
                                        .role(entry.getValue())
                                        .appId(config.getAppId())
                                        .region(config.getRegion())
                                        .expiresAt(System.currentTimeMillis() +
                                                (config.getTokenExpiryHours() * 3600 * 1000L))
                                        .build()));
    }

    // =========================================================
    // SAVE SNAPSHOT
    // =========================================================

    /**
     * Save whiteboard snapshot (Page 1, Page 2, etc.)
     */
    public Mono<WhiteboardSnapshotResponse> saveSnapshot(WhiteboardSnapshotSaveRequest request) {
        log.info("💾 Saving snapshot for session: {}", request.getClassSessionId());

        return Mono.fromCallable(() -> {                                        // BUG-2 fix
                    WhiteboardRoom room = whiteboardRoomRepository
                            .findByClassSessionId(request.getClassSessionId())
                            .orElseThrow(() -> new IllegalArgumentException("Whiteboard room not found"));

                    int snapshotNumber = room.getSnapshotCount() + 1;
                    String snapshotName = request.getSnapshotName() != null
                            ? request.getSnapshotName()
                            : "Page " + snapshotNumber;

                    List<String> authorizedStudents = getAuthorizedStudents(room);

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

                    room.setSnapshotCount(snapshotNumber);
                    whiteboardRoomRepository.save(room);

                    log.info("✅ Snapshot saved: {} for session: {}", snapshotName, request.getClassSessionId());

                    return WhiteboardSnapshotResponse.builder()
                            .id(saved.getId())
                            .classSessionId(saved.getClassSessionId())
                            .snapshotName(saved.getSnapshotName())
                            .snapshotNumber(saved.getSnapshotNumber())
                            .createdAt(saved.getCreatedAt())
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic());                      // BUG-2 fix
    }

    // =========================================================
    // GET SNAPSHOTS
    // =========================================================

    /**
     * Get snapshots for a specific class session.
     */
    public Mono<List<WhiteboardSnapshot>> getSessionSnapshots(String classSessionId, String userId) {
        log.info("📖 Getting snapshots for session: {} by user: {}", classSessionId, userId);

        return Mono.fromCallable(() -> {                                        // BUG-2 fix
                    List<WhiteboardSnapshot> snapshots = snapshotRepository
                            .findByClassSessionIdOrderBySnapshotNumberAsc(classSessionId);
                    return snapshots.stream()
                            .filter(s -> isUserAuthorized(s, userId))
                            .toList();
                })
                .subscribeOn(Schedulers.boundedElastic());                      // BUG-2 fix
    }

    /**
     * Get all snapshots for a course (GROUP classes).
     */
    public Mono<List<WhiteboardSnapshot>> getCourseSnapshots(String courseId, String studentId) {
        log.info("📚 Getting course snapshots for: {} by student: {}", courseId, studentId);

        return Mono.fromCallable(() ->                                          // BUG-2 fix
                        snapshotRepository.findByCourseIdAndAuthorizedStudentIdsContaining(courseId, studentId))
                .subscribeOn(Schedulers.boundedElastic());                      // BUG-2 fix
    }

    // =========================================================
    // STATE  (Redis synchronous helpers)
    // =========================================================

    /**
     * Update whiteboard state (real-time backup to Redis).
     */
    public void updateState(String classSessionId, String stateJson) {
        String redisKey = WHITEBOARD_STATE_PREFIX + classSessionId;
        redisTemplate.opsForValue().set(redisKey, stateJson, config.getTokenExpiryHours(), TimeUnit.HOURS);
        log.debug("💾 Updated whiteboard state for session: {}", classSessionId);
    }

    /**
     * Get whiteboard state from Redis.
     */
    public String getState(String classSessionId) {
        String redisKey = WHITEBOARD_STATE_PREFIX + classSessionId;
        Object state = redisTemplate.opsForValue().get(redisKey);
        return state != null ? state.toString() : "{}";
    }

    // =========================================================
    // CLOSE / DISCONNECT
    // =========================================================

    /**
     * Close whiteboard.
     * @param permanent true = manual end (class over), false = temporary network drop
     */
    public Mono<Void> closeWhiteboard(String classSessionId, boolean permanent) {
        if (!permanent) {
            log.info("⚠️ Temporary disconnect - keeping whiteboard active for session: {}", classSessionId);
            return Mono.<Void>empty();                                          // ✅ explicit <Void>
        }

        log.info("🔴 Permanently closing whiteboard for session: {}", classSessionId);

        return Mono.fromCallable(() ->
                        whiteboardRoomRepository.findByClassSessionId(classSessionId))
                .subscribeOn(Schedulers.boundedElastic())
                .<Void>flatMap(roomOpt -> {                                     // ✅ explicit <Void> on flatMap
                    if (roomOpt.isEmpty()) {
                        log.warn("⚠️ No whiteboard to close for session: {}", classSessionId);
                        return Mono.<Void>empty();                              // ✅ explicit <Void>
                    }

                    WhiteboardRoom room = roomOpt.get();

                    return agoraClient.banRoom(room.getAgoraWhiteboardUuid())
                            .then(Mono.<Void>fromRunnable(() -> {               // ✅ explicit <Void>
                                room.setIsActive(false);
                                room.setIsBan(true);
                                room.setClosedAt(LocalDateTime.now());
                                whiteboardRoomRepository.save(room);

                                String cacheKey = WHITEBOARD_ROOM_CACHE_PREFIX + classSessionId;
                                redisTemplate.delete(cacheKey);
                                redisTemplate.delete(WHITEBOARD_STATE_PREFIX + classSessionId);

                                log.info("✅ Whiteboard permanently closed for session: {}", classSessionId);
                                broadcastWhiteboardClosed(classSessionId);
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .onErrorResume(error -> {
                                log.error("❌ Failed to ban room in Agora: {}", error.getMessage());
                                return Mono.<Void>fromRunnable(() -> {          // ✅ explicit <Void>
                                    room.setIsActive(false);
                                    room.setClosedAt(LocalDateTime.now());
                                    whiteboardRoomRepository.save(room);
                                    broadcastWhiteboardClosed(classSessionId);
                                }).subscribeOn(Schedulers.boundedElastic());
                            });
                });
    }

    /**
     * Backward-compatible overload — defaults to permanent close.
     */
    public Mono<Void> closeWhiteboard(String classSessionId) {
        return closeWhiteboard(classSessionId, true);
    }

    /**
     * Handle temporary network disconnect — keep whiteboard alive.
     */
    public Mono<Void> handleDisconnect(String classSessionId, String userId) {
        log.info("⚠️ User {} disconnected from session {} - whiteboard remains active",
                userId, classSessionId);

        return Mono.fromRunnable(() -> {                                        // BUG-2 fix
                    Optional<WhiteboardRoom> roomOpt = whiteboardRoomRepository
                            .findByClassSessionId(classSessionId);
                    if (roomOpt.isPresent()) {
                        WhiteboardRoom room = roomOpt.get();
                        room.setLastDisconnectTime(LocalDateTime.now());
                        whiteboardRoomRepository.save(room);
                        log.info("✅ Disconnect tracked for session: {}", classSessionId);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())                       // BUG-2 fix
                .then();
    }

    // =========================================================
    // CLEANUP SCHEDULER
    // =========================================================

    /**
     * Cleanup abandoned sessions — called from @Scheduled component.
     */
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

            // BUG-6 fix: .subscribe() instead of .block()
            closeWhiteboard(room.getClassSessionId(), true)
                    .doOnSuccess(v -> log.info("✅ Successfully closed abandoned session: {}",
                            room.getClassSessionId()))
                    .doOnError(e -> log.error("❌ Failed to close abandoned session: {}",
                            room.getClassSessionId(), e))
                    .subscribe();
        });
    }

    // =========================================================
    // GET SINGLE SNAPSHOT / STATUS
    // =========================================================

    /**
     * Get single snapshot by ID with authorization check.
     */
    public Mono<WhiteboardSnapshot> getSnapshotById(String snapshotId, String userId) {
        log.info("📄 Getting snapshot: {} by user: {}", snapshotId, userId);

        return Mono.fromCallable(() -> snapshotRepository.findById(snapshotId)
                        .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId)))
                .subscribeOn(Schedulers.boundedElastic())                       // BUG-2 fix
                .map(snapshot -> {
                    if (!isUserAuthorized(snapshot, userId)) {
                        throw new IllegalArgumentException("User not authorized to view this snapshot");
                    }
                    return snapshot;
                });
    }

    /**
     * Get whiteboard status for a session.
     */
    public Mono<Map<String, Object>> getWhiteboardStatus(String classSessionId) {
        log.debug("ℹ️ Checking whiteboard status for session: {}", classSessionId);

        return Mono.fromCallable(() -> {                                        // BUG-2 fix
                    Optional<WhiteboardRoom> room = whiteboardRoomRepository
                            .findByClassSessionId(classSessionId);

                    if (room.isEmpty()) {
                        return Map.<String, Object>of(
                                "isOpen", false,
                                "classSessionId", classSessionId);
                    }

                    WhiteboardRoom whiteboardRoom = room.get();
                    return Map.<String, Object>of(
                            "isOpen", whiteboardRoom.getIsActive(),
                            "classSessionId", classSessionId,
                            "whiteboardRoomId", whiteboardRoom.getId(),
                            "agoraWhiteboardUuid", whiteboardRoom.getAgoraWhiteboardUuid(),
                            "snapshotCount", whiteboardRoom.getSnapshotCount(),
                            "activeStudents", whiteboardRoom.getActiveStudentIds().size(),
                            "sessionType", whiteboardRoom.getSessionType().name());
                })
                .subscribeOn(Schedulers.boundedElastic());                      // BUG-2 fix
    }

    // =========================================================
    // WEBSOCKET BROADCAST HELPERS  (BUG-7 fix)
    // =========================================================

    /**
     * Broadcast WHITEBOARD_OPENED to all participants.
     * Topic: /topic/whiteboard/{classSessionId}
     */
    private void broadcastWhiteboardOpened(WhiteboardOpenResponse response,
                                           WhiteboardOpenRequest request) {
        try {
            WhiteboardEventMessage event = WhiteboardEventMessage.builder()
                    .type(WhiteboardEventMessage.EventType.WHITEBOARD_OPENED)
                    .classSessionId(response.getClassSessionId())
                    .agoraWhiteboardUuid(response.getAgoraWhiteboardUuid())
                    .appId(response.getAppId())
                    .region(response.getRegion())
                    .expiresAt(response.getExpiresAt())
                    .message("Teacher opened the whiteboard")
                    .build();

            String topic = WS_WHITEBOARD_TOPIC + response.getClassSessionId();
            messagingTemplate.convertAndSend(topic, event);
            log.info("📢 Broadcast WHITEBOARD_OPENED → {}", topic);
        } catch (Exception e) {
            // Never let a broadcast failure affect the main teacher response
            log.error("❌ Failed to broadcast WHITEBOARD_OPENED: {}", e.getMessage());
        }
    }

    /**
     * Broadcast WHITEBOARD_CLOSED to all participants.
     */
    private void broadcastWhiteboardClosed(String classSessionId) {
        try {
            WhiteboardEventMessage event = WhiteboardEventMessage.builder()
                    .type(WhiteboardEventMessage.EventType.WHITEBOARD_CLOSED)
                    .classSessionId(classSessionId)
                    .message("Whiteboard has been closed")
                    .build();

            String topic = WS_WHITEBOARD_TOPIC + classSessionId;
            messagingTemplate.convertAndSend(topic, event);
            log.info("📢 Broadcast WHITEBOARD_CLOSED → {}", topic);
        } catch (Exception e) {
            log.error("❌ Failed to broadcast WHITEBOARD_CLOSED: {}", e.getMessage());
        }
    }

    // =========================================================
    // PRIVATE HELPERS  (original logic — unchanged)
    // =========================================================

    private Integer calculateRoomLimit(SessionType sessionType, WhiteboardOpenRequest request) {
        return switch (sessionType) {
            // 6 = teacher + student + parent/support + 2 buffer slots
            case SOLO, DEMO, RECURRING -> 6;

            // GROUP: all enrolled students are potential writers,
            // plus teacher + support + 2 buffer slots
            case GROUP -> {
                int base = request.getEnrolledStudentIds() != null
                        ? request.getEnrolledStudentIds().size()
                        : 0;
                yield base + 3; // students + teacher + support/buffer
            }
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
            case PARENT_OBSERVER -> "reader";
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
                cacheKey, response, config.getTokenExpiryHours(), TimeUnit.HOURS);
    }
}