package com.rotiprata.api.content.dto;

public record ContentQuizSubmitResponse(
    int score,
    int maxScore,
    double percentage,
    boolean passed
) {}
