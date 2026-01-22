package com.tcon.communication_service.whiteboard.controller;

import com.tcon.communication_service.whiteboard.service.WhiteboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for synchronizing whiteboard state.
 */
@RestController
@RequestMapping("/api/whiteboard")
@RequiredArgsConstructor
public class WhiteboardController {

    private final WhiteboardService whiteboardService;

    @PutMapping("/{sessionId}/state")
    public ResponseEntity<Void> updateState(@PathVariable String sessionId,
                                            @RequestBody String stateJson) {
        whiteboardService.updateState(sessionId, stateJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}/state")
    public ResponseEntity<String> getState(@PathVariable String sessionId) {
        return ResponseEntity.ok(whiteboardService.getState(sessionId));
    }
}

