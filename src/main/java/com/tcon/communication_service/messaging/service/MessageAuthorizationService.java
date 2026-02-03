package com.tcon.communication_service.messaging.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Message Authorization Service
 * Handles authorization logic for messaging between users
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageAuthorizationService {

    private final EnrollmentValidationService enrollmentValidationService;
    private final WebClient.Builder webClientBuilder;

    /**
     * Check if senderId can message receiverId
     */
    public boolean canUserMessageUser(String senderId, String receiverId) {
        log.info("Checking if {} can message {}", senderId, receiverId);

        // Get sender and receiver roles
        String senderRole = getUserRole(senderId);
        String receiverRole = getUserRole(receiverId);

        if (senderRole == null || receiverRole == null) {
            log.warn("Could not determine roles for sender {} or receiver {}", senderId, receiverId);
            return false;
        }

        // Admin can message anyone
        if ("ADMIN".equals(senderRole)) {
            log.debug("Sender is admin - allowing message");
            return true;
        }

        // Check role-based rules
        return switch (senderRole) {
            case "STUDENT" -> canStudentMessage(senderId, receiverId, receiverRole);
            case "TEACHER" -> canTeacherMessage(senderId, receiverId, receiverRole);
            case "PARENT" -> canParentMessage(senderId, receiverId, receiverRole);
            default -> {
                log.warn("Unknown sender role: {}", senderRole);
                yield false;
            }
        };
    }

    /**
     * Check if student can message receiver
     * Students can only message teachers of courses they're enrolled in
     */
    private boolean canStudentMessage(String studentId, String receiverId, String receiverRole) {
        if (!"TEACHER".equals(receiverRole)) {
            log.debug("Student {} cannot message non-teacher role: {}", studentId, receiverRole);
            return false;
        }

        // Check enrollment relationship
        boolean hasEnrollment = enrollmentValidationService.canUsersCommunicate(studentId, receiverId);
        log.debug("Student {} enrollment with teacher {}: {}", studentId, receiverId, hasEnrollment);
        return hasEnrollment;
    }

    /**
     * Check if teacher can message receiver
     * Teachers can message students enrolled in their courses and parents of those students
     */
    private boolean canTeacherMessage(String teacherId, String receiverId, String receiverRole) {
        if ("STUDENT".equals(receiverRole)) {
            // Check if student is enrolled in teacher's course
            boolean hasEnrollment = enrollmentValidationService.canUsersCommunicate(receiverId, teacherId);
            log.debug("Teacher {} enrollment with student {}: {}", teacherId, receiverId, hasEnrollment);
            return hasEnrollment;
        }

        if ("PARENT".equals(receiverRole)) {
            // Check if parent's child is enrolled in teacher's course
            boolean canMessage = canTeacherMessageParent(teacherId, receiverId);
            log.debug("Teacher {} can message parent {}: {}", teacherId, receiverId, canMessage);
            return canMessage;
        }

        log.debug("Teacher {} cannot message role: {}", teacherId, receiverRole);
        return false;
    }

    /**
     * Check if parent can message receiver
     * Parents can only message teachers of courses their children are enrolled in
     */
    private boolean canParentMessage(String parentId, String receiverId, String receiverRole) {
        if (!"TEACHER".equals(receiverRole)) {
            log.debug("Parent {} cannot message non-teacher role: {}", parentId, receiverRole);
            return false;
        }

        // Get parent's children from auth-service
        String[] childIds = getParentChildren(parentId);
        if (childIds == null || childIds.length == 0) {
            log.debug("Parent {} has no children", parentId);
            return false;
        }

        // Check if any child is enrolled with this teacher
        for (String childId : childIds) {
            if (enrollmentValidationService.canUsersCommunicate(childId, receiverId)) {
                log.debug("Parent {}'s child {} is enrolled with teacher {}", parentId, childId, receiverId);
                return true;
            }
        }

        log.debug("No enrollment found for parent {}'s children with teacher {}", parentId, receiverId);
        return false;
    }

    /**
     * Check if teacher can message parent
     */
    private boolean canTeacherMessageParent(String teacherId, String parentId) {
        String[] childIds = getParentChildren(parentId);
        if (childIds == null || childIds.length == 0) {
            return false;
        }

        // Check if any child is enrolled in teacher's course
        for (String childId : childIds) {
            if (enrollmentValidationService.canUsersCommunicate(childId, teacherId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get user role from auth-service
     */
    private String getUserRole(String userId) {
        try {
            Map<String, Object> userInfo = webClientBuilder.build()
                    .get()
                    .uri("http://auth-user-service/api/users/" + userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (userInfo != null && userInfo.containsKey("role")) {
                return userInfo.get("role").toString();
            }
        } catch (Exception e) {
            log.error("Error fetching user role for {}: {}", userId, e.getMessage());
        }
        return null;
    }

    /**
     * Get parent's children IDs from auth-service
     */
    private String[] getParentChildren(String parentId) {
        try {
            Map<String, Object> parentInfo = webClientBuilder.build()
                    .get()
                    .uri("http://auth-user-service/api/parents/" + parentId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (parentInfo != null && parentInfo.containsKey("childUserIds")) {
                Object childIds = parentInfo.get("childUserIds");
                if (childIds instanceof java.util.List) {
                    return ((java.util.List<?>) childIds).stream()
                            .map(Object::toString)
                            .toArray(String[]::new);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching parent children for {}: {}", parentId, e.getMessage());
        }
        return new String[0];
    }
}
