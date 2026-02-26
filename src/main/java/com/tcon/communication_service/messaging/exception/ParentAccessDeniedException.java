package com.tcon.communication_service.messaging.exception;

public class ParentAccessDeniedException extends RuntimeException {
    public ParentAccessDeniedException(String message) {
        super(message);
    }
}
