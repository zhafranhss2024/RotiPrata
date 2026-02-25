package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record LessonQuizAnswerRequest(
    @NotBlank String attemptId,
    @NotBlank String questionId,
    @NotNull Map<String, Object> response
) {}
