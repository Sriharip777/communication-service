// UserServiceClient.java
package com.tcon.communication_service.client;

import com.tcon.communication_service.messaging.dto.ContactDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(
        name = "auth-user-service",
        contextId = "userServiceClient"
        // no url -> use Eureka
)
public interface UserServiceClient {

    @GetMapping("/api/users/contacts")
    List<ContactDto> getContacts(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole
    );

}
