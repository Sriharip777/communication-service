package com.tcon.communication_service.whiteboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket event pushed to students/parents when teacher opens or closes whiteboard.
 * Broadcast topic: /topic/whiteboard/{classSessionId}
 *
 * Frontend:
 *   stompClient.subscribe(`/topic/whiteboard/${classSessionId}`, msg => {
 *       const event = JSON.parse(msg.body);
 *       if (event.type === 'WHITEBOARD_OPENED') openWhiteboardViewer(event);
 *       if (event.type === 'WHITEBOARD_CLOSED') closeWhiteboardViewer();
 *   });
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhiteboardEventMessage {

    public enum EventType {
        WHITEBOARD_OPENED,
        WHITEBOARD_CLOSED
    }

    private EventType type;
    private String classSessionId;
    private String agoraWhiteboardUuid;
    private String appId;
    private String region;
    private Long expiresAt;
    private String message;
}