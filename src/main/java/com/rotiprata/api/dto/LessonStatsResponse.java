package com.rotiprata.api.dto;

public record LessonStatsResponse(
    int lessonsEnrolled,
    int lessonsCompleted,
    int currentStreak,
    int conceptsMastered,
    double hoursLearned
) {}
