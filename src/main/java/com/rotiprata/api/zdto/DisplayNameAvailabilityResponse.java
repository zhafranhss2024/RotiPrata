package com.rotiprata.api.zdto;

public record DisplayNameAvailabilityResponse(
    boolean available,
    String normalized
) {}
