package com.rotiprata.api.exception;

/**
 * Documents the moderation service exception type.
 */
public class ModerationServiceException extends RuntimeException {
    /**
     * Creates a moderation service exception instance with its collaborators.
     */
    public ModerationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
