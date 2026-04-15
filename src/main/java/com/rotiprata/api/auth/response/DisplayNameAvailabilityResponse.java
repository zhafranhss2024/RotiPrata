package com.rotiprata.api.auth.response;

public record DisplayNameAvailabilityResponse(
    boolean available,
    String normalized
) {}
