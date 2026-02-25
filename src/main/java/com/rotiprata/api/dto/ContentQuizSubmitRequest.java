package com.rotiprata.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ContentQuizSubmitRequest(
    @NotNull @Min(0) Integer score,
    @NotNull @Min(1) Integer maxScore,
    Map<String, Object> answers,
    @Min(0) @Max(86400) Integer timeTakenSeconds
) {}
