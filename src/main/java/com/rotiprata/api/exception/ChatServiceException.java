package com.rotiprata.api.exception;

public class ChatServiceException extends RuntimeException {
    public ChatServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
