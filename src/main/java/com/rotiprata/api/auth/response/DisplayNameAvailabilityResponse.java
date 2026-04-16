package com.rotiprata.api.auth.response;

/**
 * Represents the display name availability response payload returned by the API layer.
 */
public record DisplayNameAvailabilityResponse(
    boolean available,
    String normalized
) {}
