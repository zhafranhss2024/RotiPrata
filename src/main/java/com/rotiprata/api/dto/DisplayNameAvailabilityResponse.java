package com.rotiprata.api.dto;

public record DisplayNameAvailabilityResponse(
    boolean available,
    String normalized
) {}
