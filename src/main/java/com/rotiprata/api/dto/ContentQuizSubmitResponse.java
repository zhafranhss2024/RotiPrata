package com.rotiprata.api.dto;

public record ContentQuizSubmitResponse(
    int score,
    int maxScore,
    double percentage,
    boolean passed
) {}
