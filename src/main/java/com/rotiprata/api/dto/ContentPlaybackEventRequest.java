package com.rotiprata.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ContentPlaybackEventRequest(
    @Min(0) Long startupMs,
    @Min(0) Integer stallCount,
    @Min(0) Long stalledMs,
    @Min(0) Long watchMs,
    Boolean playSuccess,
    @Min(0) Integer autoplayBlockedCount,
    @Size(max = 64) String networkType,
    @Size(max = 512) String userAgent
) {}
