// ParentServiceClient.java
package com.tcon.communication_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;

@FeignClient(
        name = "auth-user-service",
        url = "${auth-user-service.url:}",
        contextId = "parentServiceClient",
        fallback = ParentServiceClient.ParentServiceClientFallback.class
)
public interface ParentServiceClient {

    @GetMapping("/api/parents/{parentId}/students")
    List<String> getChildStudentIds(@PathVariable("parentId") String parentId);

    @GetMapping("/api/students/{studentId}/parents")
    List<String> getParentIds(@PathVariable("studentId") String studentId);

    class ParentServiceClientFallback implements ParentServiceClient {
        @Override
        public List<String> getChildStudentIds(String parentId) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getParentIds(String studentId) {
            return Collections.emptyList();
        }
    }
}