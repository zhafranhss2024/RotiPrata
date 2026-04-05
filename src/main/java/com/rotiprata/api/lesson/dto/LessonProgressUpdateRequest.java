package com.rotiprata.api.lesson.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LessonProgressUpdateRequest(
    @NotNull @Min(0) @Max(100) Integer progress
) {}
