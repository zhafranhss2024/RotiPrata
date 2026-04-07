package com.rotiprata.api.exception;

public class ModerationServiceException extends RuntimeException {
    public ModerationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}