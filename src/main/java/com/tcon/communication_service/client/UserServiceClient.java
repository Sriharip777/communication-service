package com.tcon.communication_service.client;

import com.tcon.communication_service.messaging.dto.ContactDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(
        name = "auth-user-service",
        url = "http://localhost:8081" // or via Eureka: name only, no url
)
public interface UserServiceClient {

    @GetMapping("/api/users/contacts")
    List<ContactDto> getContacts(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole
    );
}