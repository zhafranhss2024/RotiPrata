package com.rotiprata.api.lesson.dto;

public record LessonHubSummaryResponse(
    int totalLessons,
    int completedLessons,
    int currentStreak
) {}
