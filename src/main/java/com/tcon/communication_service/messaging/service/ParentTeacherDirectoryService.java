package com.tcon.communication_service.messaging.service;

import com.tcon.communication_service.client.ParentServiceClient;
import com.tcon.communication_service.client.UserServiceClient;
import com.tcon.communication_service.messaging.dto.ContactDto;
import com.tcon.communication_service.messaging.entity.Conversation;
import com.tcon.communication_service.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentTeacherDirectoryService {

    private final ConversationRepository conversationRepository;
    private final ParentServiceClient parentServiceClient;
    private final UserServiceClient userServiceClient;

    /**
     * Return teachers who have conversations with any of the parent's children.
     */
    public List<ContactDto> getTeachersForParent(String parentId) {
        log.info("🔍 Fetching teachers for parent {}", parentId);

        // 1) child userIds for this parent (studentIds)
        List<String> childIds = parentServiceClient.getChildStudentIds(parentId);
        if (childIds == null || childIds.isEmpty()) {
            log.info("No children found for parent {}", parentId);
            return List.of();
        }

        // 2) conversations where any child is a participant
        List<Conversation> conversations = conversationRepository.findByParticipantIdsIn(childIds);
        if (conversations.isEmpty()) {
            log.info("No conversations found for children of parent {}", parentId);
            return List.of();
        }

        // 3) collect teacherIds (participants that are not children)
        Set<String> teacherIds = new HashSet<>();
        for (Conversation conv : conversations) {
            for (String pid : conv.getParticipantIds()) {
                if (!childIds.contains(pid)) {
                    teacherIds.add(pid);
                }
            }
        }

        if (teacherIds.isEmpty()) {
            log.info("No teachers found for parent {}", parentId);
            return List.of();
        }

        log.info("👧 Child IDs for parent {}: {}", parentId, childIds);
        log.info("💬 Conversations for children: {}", conversations.size());
        log.info("👨‍🏫 Teacher IDs from conversations: {}", teacherIds);


        // 4) Reuse contacts API: ask auth-user-service to return contact info for this parent,
        // then filter that list to only include teacherIds we've detected above.
        List<ContactDto> allContacts = userServiceClient.getContacts(parentId, "PARENT");
        log.info("📇 Contacts from auth for parent {}: {}", parentId, allContacts.size());
        for (ContactDto c : allContacts) {
            log.info("📇 Contact: id={}, role={}", c.getId(), c.getRole());
        }

        Map<String, ContactDto> byId = new HashMap<>();
        for (ContactDto c : allContacts) {
            byId.put(c.getId(), c);
        }

        List<ContactDto> teachers = new ArrayList<>();
        for (String tid : teacherIds) {
            ContactDto contact = byId.get(tid);
            if (contact != null && "TEACHER".equalsIgnoreCase(contact.getRole())) {
                teachers.add(contact);
            }
        }

        log.info("✅ Found {} teachers for parent {}", teachers.size(), parentId);
        return teachers;
    }
}
