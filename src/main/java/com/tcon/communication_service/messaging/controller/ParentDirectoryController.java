package com.tcon.communication_service.messaging.controller;
import com.tcon.communication_service.messaging.dto.ContactDto;
import com.tcon.communication_service.messaging.service.ParentTeacherDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/messages/parent")
@RequiredArgsConstructor
public class ParentDirectoryController {

    private final ParentTeacherDirectoryService directoryService;

    @GetMapping("/teachers")
    public ResponseEntity<List<ContactDto>> getMyTeachers(
            @RequestHeader("X-User-Id") String parentId,
            @RequestHeader("X-User-Role") String role
    ) {
        log.info("📇 Getting teachers for parent {} (role={})", parentId, role);

        if (!"PARENT".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        List<ContactDto> teachers = directoryService.getTeachersForParent(parentId);
        return ResponseEntity.ok(teachers);
    }
}