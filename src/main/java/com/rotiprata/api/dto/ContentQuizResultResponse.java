package com.rotiprata.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ContentQuizResultResponse(
    Integer score,
    Integer maxScore,
    Double percentage,
    Boolean passed,
    Map<String, Object> answers,
    Integer timeTakenSeconds,
    OffsetDateTime attemptedAt
) {}
