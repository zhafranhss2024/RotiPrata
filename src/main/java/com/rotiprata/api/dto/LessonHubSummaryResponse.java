package com.rotiprata.api.dto;

public record LessonHubSummaryResponse(
    int totalLessons,
    int completedLessons,
    int currentStreak
) {}
