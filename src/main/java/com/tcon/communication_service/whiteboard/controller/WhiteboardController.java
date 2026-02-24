//Whiteboard Controller


package com.tcon.communication_service.whiteboard.controller;

import com.tcon.communication_service.whiteboard.dto.*;
import com.tcon.communication_service.whiteboard.entity.WhiteboardSnapshot;
import com.tcon.communication_service.whiteboard.service.WhiteboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Whiteboard Controller
 * REST endpoints for Agora Interactive Whiteboard
 *
 * @author Senior Developer
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/whiteboard")
@RequiredArgsConstructor
@Validated
public class WhiteboardController {

    private final WhiteboardService whiteboardService;

    /**
     * Open whiteboard in active video session (Teacher initiates)
     * POST /api/whiteboard/open
     *
     * Called when teacher clicks "Open Whiteboard" button during video call
     */
    @PostMapping("/open")
    public Mono<ResponseEntity<WhiteboardOpenResponse>> openWhiteboard(
            @Valid @RequestBody WhiteboardOpenRequest request) {
        log.info("📋 API: Opening whiteboard for class session: {}", request.getClassSessionId());

        return whiteboardService.openWhiteboardInSession(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorResume(IllegalArgumentException.class, error -> {
                    log.error("❌ Invalid request: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(IllegalStateException.class, error -> {
                    log.error("❌ Invalid state: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to open whiteboard", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Access whiteboard (Student/Parent joins)
     * POST /api/whiteboard/access
     *
     * Auto-grants access if user is enrolled in session
     */
    @PostMapping("/access")
    public Mono<ResponseEntity<WhiteboardAccessResponse>> accessWhiteboard(
            @Valid @RequestBody WhiteboardAccessRequest request) {
        log.info("🎫 API: User {} accessing whiteboard in session {}",
                request.getUserId(), request.getClassSessionId());

        return whiteboardService.accessWhiteboardInSession(request)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, error -> {
                    log.error("❌ Unauthorized access: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                })
                .onErrorResume(IllegalStateException.class, error -> {
                    log.error("❌ Invalid state: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to access whiteboard", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Save whiteboard snapshot (Teacher clicks "Save Page")
     * POST /api/whiteboard/snapshot/save
     */
    @PostMapping("/snapshot/save")
    public Mono<ResponseEntity<WhiteboardSnapshotResponse>> saveSnapshot(
            @Valid @RequestBody WhiteboardSnapshotSaveRequest request) {
        log.info("💾 API: Saving snapshot for session: {}", request.getClassSessionId());

        return whiteboardService.saveSnapshot(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorResume(IllegalArgumentException.class, error -> {
                    log.error("❌ Invalid request: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to save snapshot", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Get all snapshots for a class session
     * GET /api/whiteboard/session/{classSessionId}/snapshots
     *
     * Returns all whiteboard pages saved during the session
     */
    @GetMapping("/session/{classSessionId}/snapshots")
    public Mono<ResponseEntity<List<WhiteboardSnapshot>>> getSessionSnapshots(
            @PathVariable String classSessionId,
            @RequestParam String userId) {
        log.info("📖 API: Getting snapshots for session: {} by user: {}",
                classSessionId, userId);

        return whiteboardService.getSessionSnapshots(classSessionId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("❌ Failed to get snapshots", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Get all snapshots for a course (GROUP classes)
     * GET /api/whiteboard/course/{courseId}/snapshots
     *
     * Returns all whiteboard pages across all sessions of the course
     */
    @GetMapping("/course/{courseId}/snapshots")
    public Mono<ResponseEntity<List<WhiteboardSnapshot>>> getCourseSnapshots(
            @PathVariable String courseId,
            @RequestParam String studentId) {
        log.info("📚 API: Getting course snapshots for: {} by student: {}",
                courseId, studentId);

        return whiteboardService.getCourseSnapshots(courseId, studentId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("❌ Failed to get course snapshots", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Get single snapshot by ID
     * GET /api/whiteboard/snapshot/{snapshotId}
     */
    @GetMapping("/snapshot/{snapshotId}")
    public Mono<ResponseEntity<WhiteboardSnapshot>> getSnapshotById(
            @PathVariable String snapshotId,
            @RequestParam String userId) {
        log.info("📄 API: Getting snapshot: {} by user: {}", snapshotId, userId);

        return whiteboardService.getSnapshotById(snapshotId, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, error -> {
                    log.error("❌ Snapshot not found or unauthorized: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                })
                .onErrorResume(error -> {
                    log.error("❌ Failed to get snapshot", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Update whiteboard state (Real-time backup)
     * PUT /api/whiteboard/{classSessionId}/state
     *
     * Called periodically by frontend to backup current state
     */
    @PutMapping("/{classSessionId}/state")
    public ResponseEntity<Void> updateState(
            @PathVariable String classSessionId,
            @RequestBody String stateJson) {
        log.debug("💾 API: Updating state for session: {}", classSessionId);

        whiteboardService.updateState(classSessionId, stateJson);
        return ResponseEntity.ok().build();
    }

    /**
     * Get whiteboard state
     * GET /api/whiteboard/{classSessionId}/state
     *
     * Retrieve last backed up state (for recovery)
     */
    @GetMapping("/{classSessionId}/state")
    public ResponseEntity<String> getState(@PathVariable String classSessionId) {
        log.debug("📖 API: Getting state for session: {}", classSessionId);

        String state = whiteboardService.getState(classSessionId);
        return ResponseEntity.ok(state);
    }

    /**
     * Close whiteboard (When video session ends)
     * DELETE /api/whiteboard/{classSessionId}
     */
    @DeleteMapping("/{classSessionId}")
    public Mono<ResponseEntity<Void>> closeWhiteboard(
            @PathVariable String classSessionId) {
        log.info("🚪 API: Closing whiteboard for session: {}", classSessionId);

        return whiteboardService.closeWhiteboard(classSessionId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(error -> {
                    log.error("❌ Failed to close whiteboard", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Check if whiteboard is open for a session
     * GET /api/whiteboard/{classSessionId}/status
     */
    @GetMapping("/{classSessionId}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getWhiteboardStatus(
            @PathVariable String classSessionId) {
        log.debug("ℹ️ API: Checking whiteboard status for session: {}", classSessionId);

        return whiteboardService.getWhiteboardStatus(classSessionId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("❌ Failed to get whiteboard status", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Health check endpoint
     * GET /api/whiteboard/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "whiteboard",
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }
}