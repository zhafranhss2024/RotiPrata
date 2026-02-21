package com.rotiprata.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateLessonProgressRequest(
    @Min(0) @Max(100) Integer progress
) {}
